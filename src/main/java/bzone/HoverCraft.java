package bzone;

import static bzone.BattleZone.to16;
import static bzone.BattleZone.wrapDelta16;
import com.badlogic.gdx.math.MathUtils;

/**
 * Hovercraft — ROM-inspired skimmer that glides with long curves, mapping to
 * class-3 flier patterns in spirit: p0 (wander): random fly with occasional
 * turns p1 (approach): fly toward player; short aim interludes p2 (dive/skim):
 * committed run, then rebuild spacing
 *
 * Engine notes: - Extends BaseTank for consistency with your actors. - Always
 * “in motion” (rarely idles); smooth heading changes. - Less eager to reverse
 * on bumps; prefers to ride them out and re-aim. - Shoots with a slightly
 * looser gate than Tank (hoverTryShoot).
 */
public class HoverCraft extends BaseTank {

    // Model
    final GameModelInstance model;

    // Tuning
    private static final float SPEED_MULT_BASE = 1.40f; // quick glide
    private static final float SPEED_MULT_DIVE = 1.55f; // committed run
    private static final float SPEED_MULT_ORBIT = 1.30f; // when circling
    private static final int MICRO_OFFSET_STEPS_MAX = 10;    // ~14°
    private static final int ORBIT_OFFSET_STEPS = 48;    // ~67.5°
    private static final int STRAFE_90_STEPS = 64;    // 90°
    private static final int DIVE_OBLIQUE_STEPS = 80;    // ~112.5° on break-away
    private static final float ORBIT_RADIUS_TARGET = 5200f;
    private static final float TOO_CLOSE_RADIUS = 1600f;
    private static final float FAR_RADIUS = 10500f;

    // Smoothing
    private boolean turnGate;                // alternate frame turning to feel glidy
    private int glideBiasSteps = 0;          // small persistent offset to heading

    // Behavior plan
    private enum Plan {
        WANDER, APPROACH, ORBIT, DIVE
    }
    private Plan plan = Plan.WANDER;
    private int orbitDir = MathUtils.randomBoolean() ? +1 : -1;

    public HoverCraft(GameModelInstance model, GameModelInstance radar, Projectile projectile) {
        super(model, null, projectile);
        this.model = model;
        this.facing = MathUtils.random(0, ANGLE_STEPS - 1);
        this.radarFacing = this.facing;
        this.turnTo = this.facing;
        this.moveCounter = 1; // force immediate plan selection
    }

    @Override
    protected void updateTank(GameContext ctx, float dt) {
        this.inst = this.model;

        if (this.moveCounter > 0) {
            this.moveCounter--;
        }

        // radar dish spins
        this.radarFacing = u8(this.radarFacing + RADAR_SPIN);

        // Hovercraft rarely uses hard reverse. If external collision set flags,
        // do a very short backstep then resume glide.
        if ((this.reverseFlags & 0x01) != 0) {
            moveBackward(ctx, dt);
            if ((this.reverseFlags & 0x02) != 0) {
                rotateLeft(dt);
            } else {
                rotateRight(dt);
            }
            if (this.moveCounter == 0) {
                this.reverseFlags &= ~0x03;
                // snap back to current plan heading
                this.turnTo = this.facing;
                this.moveCounter = Math.max(6, FORWARD_TIME_FRAMES / 2);
            }
            tryShootPlayer(ctx);
            return;
        }

        // Distance & angle to player (16-bit world)
        float dx16 = wrapDelta16(to16(ctx.playerX) - to16(this.pos.x));
        float dz16 = wrapDelta16(to16(ctx.playerZ) - to16(this.pos.z));
        float dist = (float) Math.sqrt(dx16 * dx16 + dz16 * dz16);
        int angToPlayer = calcAngleToPlayer(ctx);

        // Re-plan when heading timer expires
        if (this.moveCounter == 0) {
            choosePlan(ctx, dist, angToPlayer);
        }

        // Smoothly steer toward target heading; apply a tiny persistent bias
        int biasedTarget = u8(this.turnTo + glideBiasSteps);
        steerSmooth(biasedTarget, dt);

        // Firing: strict plus a small hover leniency
        tryShootPlayer(ctx);
        hoverTryShoot(ctx, dist);

        // Forward motion depends on plan
        switch (plan) {
            case DIVE:
                forward(ctx, SPEED_MULT_DIVE, dt);
                break;
            case ORBIT:
                forward(ctx, SPEED_MULT_ORBIT, dt);
                break;
            default: // WANDER / APPROACH
                forward(ctx, SPEED_MULT_BASE, dt);
                break;
        }
    }

    private void choosePlan(GameContext ctx, float dist, int angToPlayer) {
        int JIT = (int) (ctx.nmiCount & 0x03L);

        // Occasionally flip orbit side and adjust glide bias
        if ((ctx.nmiCount & 0x1FL) == 0L) {
            orbitDir = -orbitDir;
            glideBiasSteps = MathUtils.random(-3, 3);
        }

        int roll = MathUtils.random(0, 255);

        if (dist > FAR_RADIUS) {
            // Far: commit to APPROACH or DIVE (long run)
            if (roll < 208) {
                plan = Plan.APPROACH; // p1
                this.turnTo = angToPlayer;
                this.moveCounter = NEW_HEADING_FRAMES + JIT;
            } else {
                plan = Plan.DIVE;     // p2 run
                boolean addMicro = MathUtils.randomBoolean();
                int off = addMicro ? MathUtils.random(0, MICRO_OFFSET_STEPS_MAX) : 0;
                this.turnTo = u8(angToPlayer + (MathUtils.randomBoolean() ? +off : -off));
                this.moveCounter = NEW_HEADING_FRAMES + 16 + JIT;
            }
            return;
        }

        if (dist < TOO_CLOSE_RADIUS) {
            // Too close: a short DIVE away then re-space
            plan = Plan.DIVE; // break-away component
            int away = u8(angToPlayer + (orbitDir > 0 ? -DIVE_OBLIQUE_STEPS : +DIVE_OBLIQUE_STEPS));
            this.turnTo = away;
            this.moveCounter = NEW_HEADING_FRAMES + 8 + JIT;
            return;
        }

        // Mid: mostly ORBIT with micro-wobbles; some WANDER to desync
        if (Math.abs(dist - ORBIT_RADIUS_TARGET) <= 2400f) {
            if (roll < 176) {
                plan = Plan.ORBIT;
                int wobble = MathUtils.random(0, MICRO_OFFSET_STEPS_MAX);
                int base = u8(angToPlayer + orbitDir * ORBIT_OFFSET_STEPS);
                this.turnTo = u8(MathUtils.randomBoolean() ? base + wobble : base - wobble);
                this.moveCounter = NEW_HEADING_FRAMES + JIT;
            } else if (roll < 216) {
                plan = Plan.WANDER;
                int off = MathUtils.random(0, MICRO_OFFSET_STEPS_MAX);
                this.turnTo = u8(this.facing + (MathUtils.randomBoolean() ? +off : -off));
                this.moveCounter = NEW_HEADING_FRAMES + JIT;
            } else {
                plan = Plan.APPROACH;
                this.turnTo = angToPlayer;
                this.moveCounter = NEW_HEADING_FRAMES + JIT;
            }
        } else {
            // Not at the ring yet: bias toward APPROACH with small wander
            if (roll < 200) {
                plan = Plan.APPROACH;
                this.turnTo = angToPlayer;
            } else {
                plan = Plan.WANDER;
                int off = MathUtils.random(0, MICRO_OFFSET_STEPS_MAX);
                this.turnTo = u8(this.facing + (MathUtils.randomBoolean() ? +off : -off));
            }
            this.moveCounter = NEW_HEADING_FRAMES + JIT;
        }
    }

    /**
     * Smooth steering: alternate-frame rotation to simulate hover glide.
     */
    private void steerSmooth(int target, float dt) {
        int delta = signed8((this.facing - target) & 0xFF);
        if (delta == 0) {
            return;
        }
        turnGate = !turnGate; // rotate only every other frame
        if (!turnGate) {
            return;
        }
        if (delta >= 0) {
            rotateRight(dt);
        } else {
            rotateLeft(dt);
        }
    }

    /**
     * Slightly relaxed firing when within reasonable range and near-aligned.
     */
    private void hoverTryShoot(GameContext ctx, float dist) {
        if (ctx.spawnProtected < SPAWN_PROTECTION) {
            return;
        }
        if (ctx.playerScore < 2000 && ctx.spawnProtected != SPAWN_PROTECTION) {
            return;
        }
        if (dist > 13000f) {
            return;
        }

        int diffSteps = Math.abs(signed8(calcAngleToPlayer(ctx) - this.facing));
        int gate = (dist < 2600f) ? 6 : 4; // modest leniency up close
        if (diffSteps <= gate) {
            this.projectile.spawnFromTank(this, ctx);
        }
    }
}
