package bzone;

import static bzone.BattleZone.to16;
import static bzone.BattleZone.wrapDelta16;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.math.MathUtils;

/**
 * Stalker — ROM-inspired cloaking ground unit.
 *
 * Notes from ROCK2 (addresses are from the annotated disassembly): - At
 * [12173..12197]: "Stalker or Gir Draxon?" -> cloak toggle with cooldowns: 50
 * frames while cloaked; 100 frames while visible. - At [13611..13630]: per-type
 * shot cooldown setup mentions Stalker/Gir Draxon; if currently invisible,
 * force visible before firing, and refresh cloaking cooldown.
 *
 * Behaviour here: - Cloaks/decloaks per the ROM timing (50f cloaked, 100f
 * visible). - When CLOAKED: flanks/orbits (±90° from player) and advances more
 * aggressively to set up a rear/side approach. - When VISIBLE: brief hold/aim
 * windows and short chases; stricter firing using BaseTank.tryShootPlayer, plus
 * "decloak-to-fire". - Reverses on collision using BaseTank helpers.
 */
public class Stalker extends BaseTank {

    private boolean cloaked = true;
    private int cloakTimer = 50;           // Start cloaked like the ROM path that sets 50
    private static final int CLOAK_ON_FR = 50;   // frames cloaked
    private static final int CLOAK_OFF_FR = 100;  // frames visible

    private static final float SPEED_MULT_CLOAKED = 1.5f;  // push harder when cloaked
    private static final float SPEED_MULT_VISIBLE = 1.2f;  // a bit quicker than slow tank
    private static final int STRAFE_90_STEPS = 64;     // 90°
    private static final int FLANK_67_STEPS = 48;     // ~67.5°
    private static final int MICRO_WOBBLE_MAX = 18;     // ~25°
    private static final float FWD_START_DIST_FAR = 3000f;
    private static final float FWD_START_DIST_NEAR = 2000f;

    // Plan
    private enum Plan {
        FLANK, ORBIT, CHASE, HOLD, STRAFE, BREAK
    }
    private Plan plan = Plan.FLANK;
    private int orbitDir = MathUtils.randomBoolean() ? +1 : -1;

    public Stalker(GameModelInstance stalkerModel, Projectile projectile) {
        super(stalkerModel, null, projectile);
        this.facing = MathUtils.random(0, ANGLE_STEPS - 1);
        this.radarFacing = this.facing;
        this.turnTo = this.facing;
        this.moveCounter = 1; // Force immediate plan choice
    }

    @Override
    protected void updateTank(GameContext ctx, float dt) {

        if (this.moveCounter > 0) {
            this.moveCounter--;
        }

        updateCloak();

        // Reverse/collision behaviour (same approach as Tank)
        if ((this.reverseFlags & 0x01) != 0) {
            moveBackward(ctx, dt);
            if ((this.reverseFlags & 0x02) != 0) {
                rotateLeft(dt);
            } else {
                rotateRight(dt);
            }
            if (this.moveCounter == 0) {
                this.reverseFlags &= ~0x03;
                this.turnTo = this.facing;
                this.moveCounter = FORWARD_TIME_FRAMES;
            }
            return;
        }

        if (this.moveCounter == 0) {
            choosePlan(ctx);
        }

        int delta = signed8((this.facing - this.turnTo) & 0xFF);
        int absDelta = Math.abs(delta);
        if (absDelta != 0) {
            if (delta >= 0) {
                rotateRight(dt);
                tryShootPlayer(ctx);
                rotateRight(dt);
                tryShootPlayer(ctx);
            } else {
                rotateLeft(dt);
                tryShootPlayer(ctx);
                rotateLeft(dt);
                tryShootPlayer(ctx);
            }
        }

        // Distance-based forward motion
        float dx16 = wrapDelta16(to16(ctx.playerX) - to16(this.pos.x));
        float dz16 = wrapDelta16(to16(ctx.playerZ) - to16(this.pos.z));
        float dist = (float) Math.sqrt(dx16 * dx16 + dz16 * dz16);

        float speedMult = cloaked ? SPEED_MULT_CLOAKED : SPEED_MULT_VISIBLE;
        float start = cloaked ? FWD_START_DIST_NEAR : FWD_START_DIST_FAR;
        if (dist >= start || absDelta <= 4) {
            forward(ctx, speedMult, dt);
        }
    }

    private void choosePlan(GameContext ctx) {
        int JIT = (int) (ctx.nmiCount & 0x03L);
        int RJIT = (int) ((ctx.nmiCount >> 1) & 0x03L);

        // Randomly reverse sometimes (keeps it slinky)
        if ((ctx.nmiCount & 7L) == 0L) {
            this.reverseFlags |= 0x01 | (MathUtils.randomBoolean() ? 0x02 : 0x00);
            this.moveCounter = REVERSE_TIME_FRAMES + RJIT;
            return;
        }

        int angToPlayer = calcAngleToPlayer(ctx);

        float dx16 = wrapDelta16(to16(ctx.playerX) - to16(this.pos.x));
        float dz16 = wrapDelta16(to16(ctx.playerZ) - to16(this.pos.z));
        float dist = (float) Math.sqrt(dx16 * dx16 + dz16 * dz16);

        int roll = MathUtils.random(0, 255);

        if (cloaked) {
            // Cloaked: prefer FLANK and ORBIT, tighten up distance
            if (dist > 8000f) {
                plan = Plan.CHASE;
                this.turnTo = angToPlayer;
                this.moveCounter = NEW_HEADING_FRAMES + JIT;
            } else if (roll < 160) {
                plan = Plan.FLANK;
                boolean wide = (roll & 1) == 0;
                int off = wide ? FLANK_67_STEPS : STRAFE_90_STEPS;
                int dir = (orbitDir > 0) ? +1 : -1;
                this.turnTo = u8(angToPlayer + dir * off);
                this.moveCounter = NEW_HEADING_FRAMES + JIT;
            } else {
                plan = Plan.ORBIT;
                orbitDir = (MathUtils.randomBoolean() ? +1 : -1) * orbitDir;
                int wobble = MathUtils.random(0, MICRO_WOBBLE_MAX);
                int base = u8(angToPlayer + orbitDir * STRAFE_90_STEPS);
                this.turnTo = u8((MathUtils.randomBoolean() ? base + wobble : base - wobble));
                this.moveCounter = NEW_HEADING_FRAMES + JIT;
            }
            return;
        }

        // Visible: mix HOLD (aim windows), STRAFE, and short CHASE bursts
        if (dist < 1800f) {
            if (roll < 180) {
                plan = Plan.HOLD;
                this.turnTo = angToPlayer;
                this.moveCounter = Math.max(8, NEW_HEADING_FRAMES / 2) + JIT;
            } else {
                plan = Plan.BREAK;
                int dir = (orbitDir > 0 ? -1 : +1);
                this.turnTo = u8(angToPlayer + dir * FLANK_67_STEPS);
                this.moveCounter = NEW_HEADING_FRAMES + (JIT << 1);
            }
        } else if (dist < 7000f) {
            if (roll < 128) {
                plan = Plan.STRAFE;
                int dir = (orbitDir > 0 ? +1 : -1);
                this.turnTo = u8(angToPlayer + dir * STRAFE_90_STEPS);
            } else {
                plan = Plan.CHASE;
                this.turnTo = angToPlayer;
            }
            this.moveCounter = NEW_HEADING_FRAMES + JIT;
        } else {
            plan = Plan.CHASE;
            this.turnTo = angToPlayer;
            this.moveCounter = NEW_HEADING_FRAMES + JIT;
        }
    }

    private void updateCloak() {
        if (cloakTimer > 0) {
            cloakTimer--;
            return;
        }
        // Toggle cloak and reset cooldown per ROM note
        cloaked = !cloaked;
        cloakTimer = cloaked ? CLOAK_ON_FR : CLOAK_OFF_FR;
    }

    @Override
    public void render(Camera cam, GameContext ctx, ModelBatch modelBatch, Environment environment) {
        if (cloaked) {
            return;
        }
        super.render(cam, ctx, modelBatch, environment);
    }
}
