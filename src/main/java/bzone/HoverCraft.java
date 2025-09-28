package bzone;

import static bzone.BattleZone.to16;
import static bzone.BattleZone.wrapDelta16;
import com.badlogic.gdx.math.MathUtils;

public class HoverCraft extends BaseTank {

    private final int bobSeed = MathUtils.random(0, 0xFF);

    public HoverCraft(GameModelInstance inst, Projectile projectile) {
        super(inst, null, projectile);
    }

    @Override
    protected void updateTank(GameContext ctx, float dt) {

        int bob = (((bobSeed ^ (int) ctx.nmiCount) & 1) == 0) ? 0 : 4;
        this.pos.y = bob;

        if (this.moveCounter > 0) {
            this.moveCounter--;
        }

        this.savePos();

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
            } else {
                rotateLeft(dt);
                tryShootPlayer(ctx);
                rotateLeft(dt);
                tryShootPlayer(ctx);
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

        float dx16 = wrapDelta16(to16(ctx.playerX) - to16(this.pos.x));
        float dz16 = wrapDelta16(to16(ctx.playerZ) - to16(this.pos.z));
        float dist = (float) Math.sqrt(dx16 * dx16 + dz16 * dz16);

        final float forwardStart = FWD_START_DISTANCE_SUPER_TANK;

        if (dist >= forwardStart) {
            float mult = SUPER_SPEED_MULT;
            forward(ctx, mult, dt);
        }
    }

    private void setTankTurnTo(GameContext ctx) {
        int JIT = (int) (ctx.nmiCount & 0x03L);

        this.turnTo = calcAngleToPlayer(ctx);
        this.reverseFlags &= ~0x01;
        this.moveCounter = NEW_HEADING_FRAMES + JIT;
    }

}
