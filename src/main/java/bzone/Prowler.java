package bzone;

import com.badlogic.gdx.math.MathUtils;

/**
 * ProwlerTank — flanker/ambusher AI. Behavior: - Maintains a ±90° flank
 * relative to the player; swaps side on a timer. - Fires only when the heading
 * is near the ideal flank arc. - Keeps to a medium standoff band; collision
 * enters reverse-recovery (in BaseTank).
 */
public class Prowler extends BaseTank {

    private static final int FLANK_DEG = 90;   // ideal offset from player
    private static final int FLANK_ARC_TOL_DEG = 20;   // fire when within ±20°
    private static final int FLANK_SWAP_MIN_FR = 40;   // frames (≈0.67s @60fps)
    private static final int FLANK_SWAP_MAX_FR = 72;   // frames (≈1.2s)

    private static final float RANGE_MIN = 1024f;  // too close → bias away
    private static final float RANGE_MAX = 3072f;  // too far → press in

    private int flankSide = MathUtils.randomBoolean() ? 0 : 1;
    private int flankSwapTimer = MathUtils.random(FLANK_SWAP_MIN_FR, FLANK_SWAP_MAX_FR);

    public Prowler(GameModelInstance inst, GameModelInstance radar) {
        super(inst, radar);
    }

    @Override
    protected void updateTank(GameContext ctx, float dt) {

        if (this.moveCounter > 0) {
            this.moveCounter--;
        }

        if (--flankSwapTimer <= 0) {
            flankSide ^= 1;
            flankSwapTimer = MathUtils.random(FLANK_SWAP_MIN_FR, FLANK_SWAP_MAX_FR);
        }

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
                this.moveCounter = NEW_HEADING_FRAMES;
            }
            return; 
        }

        if (this.moveCounter == 0) {
            final int base = calcAngleToPlayer(ctx);
            final int offset = degToSteps(FLANK_DEG);      // 90° → 64 steps
            this.turnTo = u8(base + (flankSide == 0 ? +offset : -offset));
            this.moveCounter = NEW_HEADING_FRAMES;
        }

        final int delta = signed8((this.facing - this.turnTo) & 0xFF);
        if (delta != 0) {
            if (delta > 0) {
                rotateRight(dt);
            } else {
                rotateLeft(dt);
            }
        }

        tryFireOnFlank(ctx);

        final float dist = distanceWrapped16(ctx.playerX, ctx.playerZ, this.pos.x, this.pos.z);
        if (dist > RANGE_MAX) {
            // far → straightforward press toward maintained flank
            forward(ctx, /*mult*/ 1.25f, dt);
        } else if (dist < RANGE_MIN) {
            // too close → bias heading 180° from player slightly to step out
            final int away = u8(calcAngleToPlayer(ctx) + 128);
            steerOnceToward(away, dt);
            forward(ctx, /*mult*/ 1.0f, dt);
        } else {
            // in band → orbit around player to keep the flank pressure
            final int around = u8(calcAngleToPlayer(ctx) + (flankSide == 0 ? +degToSteps(45) : -degToSteps(45)));
            steerOnceToward(around, dt);
            forward(ctx, /*mult*/ 1.0f, dt);
        }
    }

    private void tryFireOnFlank(GameContext ctx) {
        if (ctx.spawnProtected < 600) {
            return;
        }
        if (ctx.playerScore < 2000 && ctx.spawnProtected != 600) {
            return;
        }

        final int base = calcAngleToPlayer(ctx);
        final int ideal = u8(base + (flankSide == 0 ? +degToSteps(FLANK_DEG) : -degToSteps(FLANK_DEG)));
        final int errSteps = Math.abs(signed8((this.facing - ideal) & 0xFF));

        if (errSteps <= degToSteps(FLANK_ARC_TOL_DEG)) {
            ctx.shooter.shoot();
        }
    }

    private void steerOnceToward(int target, float dt) {
        final int d = signed8((this.facing - target) & 0xFF);
        if (d == 0) {
            return;
        }
        if (d > 0) {
            rotateRight(dt);
        } else {
            rotateLeft(dt);
        }
    }


}
