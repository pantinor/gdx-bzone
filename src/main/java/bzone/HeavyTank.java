package bzone;

import static bzone.BattleZone.to16;
import static bzone.BattleZone.wrapDelta16;
import com.badlogic.gdx.math.MathUtils;

/**
 * HeavyTank — ROM-inspired "heavy" ground unit.
 *
 * Intent (from ROCK2 class-1 ground patterns): - Prefers the "sit & rotate,
 * shoot" behaviour (pattern p1) at mid range. - Moves forward less frequently
 * and only when far or well-aligned. - When the player is too close, backs off
 * (explicit reverse phase). - Fewer strafes; small random heading adjustments
 * (p0/p4 flavour). - Turns slower (gated) to feel heavy.
 *
 * This implementation keeps to BaseTank helpers and mirrors Tank wiring.
 */
public class HeavyTank extends BaseTank {

    private static final float HEAVY_SPEED_MULT = 0.85f;   // slower than default tank
    private static final float FWD_START_DISTANCE_FAR = 4096f;   // only advance when far
    private static final float FWD_START_DISTANCE_NEAR = 2048f;   // or when nearly aligned
    private static final int MICRO_ADJUST_MAX_STEPS = 12;      // ~17° jitter
    private static final int STRAFE_90_STEPS = 64;      // rarely used
    private static final int RETREAT_TRIGGER_DIST = 2000;    // too close -> retreat
    private static final int RETREAT_ANGLE_STEPS = 96;      // ~135° oblique while reversing
    private static final int AIM_WINDOW_MIN = 20;      // frames
    private static final int AIM_WINDOW_MAX = 44;      // frames

    private enum Plan {
        AIM, APPROACH, ADJUST, STRAFE, RETREAT
    }
    private Plan plan = Plan.AIM;

    // Rotate gating to feel heavier: skip turning every other frame
    private boolean turnGate;

    public HeavyTank(GameModelInstance tankModel, Projectile projectile) {
        super(tankModel, null, projectile);
        this.facing = MathUtils.random(0, ANGLE_STEPS - 1);
        this.radarFacing = this.facing;
        this.turnTo = this.facing;
        this.moveCounter = 1; // trigger immediate plan choice
    }

    @Override
    protected void updateTank(GameContext ctx, float dt) {

        if (this.moveCounter > 0) {
            this.moveCounter--;
        }

        // Spin radar
        this.radarFacing = u8(this.radarFacing + RADAR_SPIN);

        // --- Reverse handling (collision or deliberate retreat) ---
        if ((this.reverseFlags & 0x01) != 0) {
            moveBackward(ctx, dt);
            // While reversing, bias away from player
            int angToPlayer = calcAngleToPlayer(ctx);
            int away = u8(angToPlayer + ((this.reverseFlags & 0x02) != 0 ? -RETREAT_ANGLE_STEPS
                    : +RETREAT_ANGLE_STEPS));
            steerToward(away, dt, /*slowTurn=*/ true);
            if (this.moveCounter == 0) {
                this.reverseFlags &= ~0x03;
                this.turnTo = this.facing;
                this.moveCounter = FORWARD_TIME_FRAMES;
            }
            // Firing while reversing is rare for heavies; keep precise only
            tryShootPlayer(ctx);
            return;
        }

        if (this.moveCounter == 0) {
            chooseHeavyPlan(ctx);
        }

        steerToward(this.turnTo, dt, /*slowTurn=*/ true);

        tryShootPlayer(ctx);

        float dx16 = wrapDelta16(to16(ctx.playerX) - to16(this.pos.x));
        float dz16 = wrapDelta16(to16(ctx.playerZ) - to16(this.pos.z));
        float dist = (float) Math.sqrt(dx16 * dx16 + dz16 * dz16);

        boolean nearlyAligned = Math.abs(signed8(calcAngleToPlayer(ctx) - this.facing)) <= 4;
        if (dist >= FWD_START_DISTANCE_FAR || (nearlyAligned && dist >= FWD_START_DISTANCE_NEAR)) {
            forward(ctx, HEAVY_SPEED_MULT, dt);
        }
    }

    /**
     * Heavier-feel steering (optionally gated to half-rate turning).
     */
    private void steerToward(int target, float dt, boolean slowTurn) {
        int delta = signed8((this.facing - target) & 0xFF);
        if (delta == 0) {
            return;
        }

        if (slowTurn) {
            // Skip every other frame to emulate sluggish turret
            turnGate = !turnGate;
            if (!turnGate) {
                return;
            }
        }

        if (delta >= 0) {
            rotateRight(dt);
        } else {
            rotateLeft(dt);
        }
    }

    private void chooseHeavyPlan(GameContext ctx) {
        int JIT = (int) (ctx.nmiCount & 0x03L);
        int RJIT = (int) ((ctx.nmiCount >> 1) & 0x03L);

        int angToPlayer = calcAngleToPlayer(ctx);

        float dx16 = wrapDelta16(to16(ctx.playerX) - to16(this.pos.x));
        float dz16 = wrapDelta16(to16(ctx.playerZ) - to16(this.pos.z));
        float dist = (float) Math.sqrt(dx16 * dx16 + dz16 * dz16);

        // Too close? Deliberate retreat phase using the reverse flags
        if (dist < RETREAT_TRIGGER_DIST) {
            plan = Plan.RETREAT;
            // pick left/right oblique away
            boolean left = MathUtils.randomBoolean();
            this.reverseFlags |= 0x01 | (left ? 0x02 : 0x00);
            this.moveCounter = REVERSE_TIME_FRAMES + RJIT;
            return;
        }

        int roll = MathUtils.random(0, 255);

        if (dist > 11000f) {
            // Very far: approach, small wobble
            plan = Plan.APPROACH;
            if (roll < 32) {
                int off = MathUtils.random(0, MICRO_ADJUST_MAX_STEPS);
                this.turnTo = u8(angToPlayer + (MathUtils.randomBoolean() ? +off : -off));
            } else {
                this.turnTo = angToPlayer;
            }
            this.moveCounter = NEW_HEADING_FRAMES + JIT;
            return;
        }

        // Mid range: mostly AIM windows (pattern p1), occasional ADJUST/STRAFE
        if (roll < 192) {
            plan = Plan.AIM;
            this.turnTo = angToPlayer;
            this.moveCounter = MathUtils.random(AIM_WINDOW_MIN, AIM_WINDOW_MAX) + JIT;
        } else if (roll < 232) {
            plan = Plan.ADJUST; // p0/p4 flavour
            int off = MathUtils.random(0, MICRO_ADJUST_MAX_STEPS);
            this.turnTo = u8(this.facing + (MathUtils.randomBoolean() ? +off : -off));
            this.moveCounter = NEW_HEADING_FRAMES + JIT;
        } else {
            plan = Plan.STRAFE; // rare for heavies
            boolean right = MathUtils.randomBoolean();
            this.turnTo = u8(angToPlayer + (right ? +STRAFE_90_STEPS : -STRAFE_90_STEPS));
            this.moveCounter = NEW_HEADING_FRAMES + JIT;
        }
    }
}
