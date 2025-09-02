package bzone;

import static bzone.BattleZone.to16;
import static bzone.BattleZone.wrapDelta16;
import com.badlogic.gdx.math.MathUtils;

public class Tank extends BaseTank {

    public Tank(GameModelInstance inst, GameModelInstance radar) {
        super(inst, radar);
    }

    @Override
    protected void updateTank(GameContext ctx, float dt) {

        if (this.moveCounter > 0) {
            this.moveCounter--;
        }

        this.savePos();

        this.radarFacing = u8(this.radarFacing + RADAR_SPIN);

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
            setTankTurnTo(ctx);
        }

        int delta = signed8((this.facing - this.turnTo) & 0xFF);
        int absDelta = Math.abs(delta);

        if (absDelta >= CLOSE_FIRING_ANGLE) {
            if (delta >= 0) {
                rotateRight(dt);
                tryShootPlayer(ctx);
                rotateRight(dt);
                tryShootPlayer(ctx);
                if (ctx.tankType == TankType.SUPER) {
                    rotateRight(dt);
                    tryShootPlayer(ctx);
                    rotateRight(dt);
                    tryShootPlayer(ctx);
                }
            } else {
                rotateLeft(dt);
                tryShootPlayer(ctx);
                rotateLeft(dt);
                tryShootPlayer(ctx);
                if (ctx.tankType == TankType.SUPER) {
                    rotateLeft(dt);
                    tryShootPlayer(ctx);
                    rotateLeft(dt);
                    tryShootPlayer(ctx);
                }
            }
            return;
        }
        if (absDelta != 0) {
            if (delta >= 0) {
                rotateRight(dt);
            } else {
                rotateLeft(dt);
            }
        }

        tryShootPlayer(ctx);

        // Distance → forward; extra push when perfectly aligned
        float dx16 = wrapDelta16(to16(ctx.playerX) - to16(this.pos.x));
        float dz16 = wrapDelta16(to16(ctx.playerZ) - to16(this.pos.z));
        float dist = (float) Math.sqrt(dx16 * dx16 + dz16 * dz16);

        final float forwardStart = (ctx.tankType == TankType.SUPER) ? FWD_START_DISTANCE_SUPER_TANK : FWD_START_DISTANCE_SLOW_TANK;

        if (dist >= forwardStart) {
            float mult = (ctx.tankType == TankType.SUPER) ? SUPER_SPEED_MULT : 1f;
            forward(ctx, mult, dt);
        }
    }

    private void setTankTurnTo(GameContext ctx) {
        int JIT = (int) (ctx.nmiCount & 0x03L);
        int RJIT = (int) ((ctx.nmiCount >> 1) & 0x03L);

        int scoreDiff = ctx.playerScore - ctx.enemyScore;

        if (scoreDiff == 0) {
            // GoMedium:
            if ((ctx.nmiCount & 7L) == 0L) {
                this.reverseFlags |= 0x01 | (MathUtils.randomBoolean() ? 0x02 : 0x00);
                this.moveCounter = REVERSE_TIME_FRAMES + RJIT;
                return;
            }

            // head 90° off the player
            // the tank’s facing direction is stored in an 8-bit angle space (0–255, i.e. 256 steps around a circle).
            // A full circle (360°) = 256 steps, so:
            // 1 step ≈ 1.40625°
            // 64 steps = 90°
            int ang = calcAngleToPlayer(ctx);

            //XOR produces a perfect ±90° offset from the target, depending on the current quadrant:
            //If the angle is pointing, say, North (0), XOR with 64 → East (90°).
            //If it’s already East (64), XOR with 64 → North (0°).
            //If South (128), XOR → West (192).
            //If West (192), XOR → South (128).
            //So this one bit-flip always pivots the direction exactly perpendicular (90°) to the target vector, 
            //but toggles between left/right depending on where the player is.
            this.turnTo = u8(ang ^ 64);

            this.reverseFlags &= ~0x01;
            this.moveCounter = NEW_HEADING_FRAMES + JIT;
            return;
        }

        if (scoreDiff < 0) {
            // GoMild (player losing): small offset from previous heading
            int offset = MathUtils.random(0, 31); // 31 steps × 1.40625° ≈ up to 43.7°.
            boolean neg = ((ctx.nmiCount & 1L) == 0L);
            this.turnTo = u8(neg ? this.turnTo - offset : this.turnTo + offset);
            this.reverseFlags &= ~0x01;
            this.moveCounter = NEW_HEADING_FRAMES + JIT;
            return;
        }

        //GoHard
        this.turnTo = calcAngleToPlayer(ctx);
        this.reverseFlags &= ~0x01;
        this.moveCounter = NEW_HEADING_FRAMES + JIT;
    }

}
