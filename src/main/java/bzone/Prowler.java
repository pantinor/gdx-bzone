package bzone;

import static bzone.BattleZone.to16;
import static bzone.BattleZone.wrapDelta16;
import com.badlogic.gdx.math.MathUtils;

/**
 * Prowler — mobile tank with a “prowl/orbit → dart in/out” behavior adapted
 * from the ROM’s ground-mover patterns, but kept within this engine’s BaseTank
 * helpers.
 *
 * High level: - Prefers to ORBIT the player at a target radius, flipping orbit
 * direction from time to time. - When far, it CHARGEs toward the player; when
 * too close, it BREAKs off with a wide oblique turn (135°) to regain spacing. -
 * Strafes at ±90° relative to the player more often than a normal tank. -
 * Shoots using the strict BaseTank tryShootPlayer gate; firing rate emerges
 * from how often alignment is achieved during turns.
 */
public class Prowler extends BaseTank {

    // Model
    final GameModelInstance tankModel;

    // Tunables (feel free to tweak until it “feels” right)
    private static final float PROWLER_SPEED_MULT = 1.35f;   // a touch faster than slow tank
    private static final float FORWARD_START_DIST = 1400f;   // starts moving forward a bit earlier
    private static final int STRAFE_90_STEPS = 64;      // 90° in 256-step circle
    private static final int OBLIQUE_135_STEPS = 96;      // 135° for break-aways
    private static final int MICRO_WOBBLE_MAX = 24;      // up to ~33.75°
    private static final int RAD_NEAR = 2800;    // “too close” radius
    private static final int RAD_TARGET = 4800;    // desired orbit radius
    private static final int RAD_FAR = 9000;    // “too far” (encourage charge)

    // Plan/state
    private enum Plan {
        ORBIT, CHARGE, BREAK, STRAFE, HOLD
    }
    private Plan plan = Plan.ORBIT;
    private int orbitDir = MathUtils.randomBoolean() ? +1 : -1;   // +left or -right in step-space

    public Prowler(GameModelInstance tankModel, Projectile projectile) {
        super(tankModel, null, projectile);
        this.tankModel = tankModel;
        this.facing = MathUtils.random(0, ANGLE_STEPS - 1);
        this.radarFacing = this.facing;
        this.turnTo = this.facing;
        this.moveCounter = 1; // trigger immediate plan selection on first update
    }

    @Override
    protected void updateTank(GameContext ctx, float dt) {

        this.inst = this.tankModel;

        if (this.moveCounter > 0) {
            this.moveCounter--;
        }

        this.savePos();

        // spin the radar
        this.radarFacing = u8(this.radarFacing + RADAR_SPIN);

        // --- Reverse handling (same contract as Tank) ---
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

        // Re-plan when the current heading timer expires
        if (this.moveCounter == 0) {
            chooseProwlerPlan(ctx);
        }

        // Turn toward the current heading
        int delta = signed8((this.facing - this.turnTo) & 0xFF);
        int absDelta = Math.abs(delta);
        if (absDelta >= CLOSE_FIRING_ANGLE) {
            // accelerate turning when far off, opportunistically firing like the original
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
        } else if (absDelta != 0) {
            if (delta >= 0) {
                rotateRight(dt);
            } else {
                rotateLeft(dt);
            }
        }

        // Precise fire once per frame if neatly aligned
        tryShootPlayer(ctx);

        // Distance-gated forward motion with a prowler bias: moves more often
        float dx16 = wrapDelta16(to16(ctx.playerX) - to16(this.pos.x));
        float dz16 = wrapDelta16(to16(ctx.playerZ) - to16(this.pos.z));
        float dist = (float) Math.sqrt(dx16 * dx16 + dz16 * dz16);

        boolean advance;
        switch (plan) {
            case HOLD:
                advance = dist > RAD_NEAR; // only creep if we're crowding the player
                break;
            case BREAK:
                advance = true; // get out quickly
                break;
            default:
                advance = (dist >= FORWARD_START_DIST) || absDelta <= 8;
        }

        if (advance) {
            forward(ctx, PROWLER_SPEED_MULT, dt);
        }
    }

    /**
     * Decide what to do next based on distance and jittered timing.
     */
    private void chooseProwlerPlan(GameContext ctx) {
        int JIT = (int) (ctx.nmiCount & 0x03L);
        int RJIT = (int) ((ctx.nmiCount >> 1) & 0x03L);

        // Occasionally perform a reverse like the base tank
        if ((ctx.nmiCount & 7L) == 0L) {
            this.reverseFlags |= 0x01 | (MathUtils.randomBoolean() ? 0x02 : 0x00);
            this.moveCounter = REVERSE_TIME_FRAMES + RJIT;
            return;
        }

        int angToPlayer = calcAngleToPlayer(ctx);

        float dx16 = wrapDelta16(to16(ctx.playerX) - to16(this.pos.x));
        float dz16 = wrapDelta16(to16(ctx.playerZ) - to16(this.pos.z));
        float dist = (float) Math.sqrt(dx16 * dx16 + dz16 * dz16);

        // Flip orbit side now and then
        if ((ctx.nmiCount & 0x1FL) == 0L) {
            orbitDir = -orbitDir;
        }

        int roll = MathUtils.random(0, 255);

        if (dist > RAD_FAR) {
            // Very far: mostly charge, sometimes strafe to avoid long straight lines
            if (roll < 208) {
                plan = Plan.CHARGE;
                this.turnTo = angToPlayer;
            } else {
                plan = Plan.STRAFE;
                this.turnTo = u8(angToPlayer + orbitDir * STRAFE_90_STEPS);
            }
            this.moveCounter = NEW_HEADING_FRAMES + JIT;
            this.reverseFlags &= ~0x01;
            return;
        }

        if (dist < RAD_NEAR) {
            // Too close: break off on a steep oblique away from the player
            plan = Plan.BREAK;
            boolean awayLeft = (orbitDir > 0);
            this.turnTo = u8(angToPlayer + (awayLeft ? -OBLIQUE_135_STEPS : +OBLIQUE_135_STEPS));
            this.moveCounter = NEW_HEADING_FRAMES + (JIT << 1);
            this.reverseFlags &= ~0x01;
            return;
        }

        // Around the preferred radius: orbit with micro-wobble, mixed with strafes
        if (roll < 160) {
            plan = Plan.ORBIT;
            int wobble = MathUtils.random(0, MICRO_WOBBLE_MAX);
            int base = u8(angToPlayer + orbitDir * STRAFE_90_STEPS);
            boolean neg = MathUtils.randomBoolean();
            this.turnTo = u8(neg ? base - wobble : base + wobble);
            this.moveCounter = NEW_HEADING_FRAMES + JIT;
        } else if (roll < 208) {
            plan = Plan.STRAFE;
            this.turnTo = u8(angToPlayer + orbitDir * STRAFE_90_STEPS);
            this.moveCounter = NEW_HEADING_FRAMES + JIT;
        } else if (roll < 232) {
            plan = Plan.HOLD; // brief aim window
            this.turnTo = angToPlayer;
            this.moveCounter = Math.max(8, NEW_HEADING_FRAMES / 2) + JIT;
        } else {
            plan = Plan.CHARGE;
            this.turnTo = angToPlayer;
            this.moveCounter = NEW_HEADING_FRAMES + JIT;
        }

        this.reverseFlags &= ~0x01;
    }
}
