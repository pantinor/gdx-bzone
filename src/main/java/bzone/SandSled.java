package bzone;

import static bzone.BattleZone.to16;
import static bzone.BattleZone.wrapDelta16;
import com.badlogic.gdx.math.MathUtils;

public class SandSled extends BaseTank {

    private static final float SPEED_MULT = 1.55f; // quick
    private static final float FWD_START_DIST_FAR = 2000f;
    private static final int MICRO_OFFSET_STEPS_MAX = 12;    // ~17°
    private static final int WIDE_OFFSET_STEPS_MAX = 24;    // ~33.75°
    private static final int PIVOT_135_STEPS = 96;    // 135°

    private int serpSign = MathUtils.randomBoolean() ? +1 : -1;
    private int serpOffset = 8;     // steps around the base heading
    private int serpTick = 0;       // frames until next flip of serpSign
    private int serpPeriod = 3;     // how long the current serpSign lasts
    private int serpDuration = 0;   // total frames to keep serpentine plan

    private enum Plan {
        SERPENTINE, CHARGE, AIM, WANDER, SPECIAL
    }
    private Plan plan = Plan.SERPENTINE;

    public SandSled(GameModelInstance model, Projectile projectile) {
        super(model, null, projectile);
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
            tryShootPlayer(ctx);
            return;
        }

        float dx16 = wrapDelta16(to16(ctx.playerX) - to16(this.pos.x));
        float dz16 = wrapDelta16(to16(ctx.playerZ) - to16(this.pos.z));
        float dist = (float) Math.sqrt(dx16 * dx16 + dz16 * dz16);
        int angToPlayer = calcAngleToPlayer(ctx);

        if (this.moveCounter == 0) {
            choosePlan(ctx, dist, angToPlayer);
        }

        switch (plan) {
            case SERPENTINE: {
                int base = angToPlayer;
                if (serpTick <= 0) {
                    serpSign = -serpSign;
                    serpTick = serpPeriod;
                    serpOffset = MathUtils.random(6, WIDE_OFFSET_STEPS_MAX);
                    serpPeriod = MathUtils.random(2, 5);
                } else {
                    serpTick--;
                }
                int target = u8(base + serpSign * serpOffset);
                steerToward(target, dt);
                forward(ctx, SPEED_MULT, dt);
                break;
            }
            case CHARGE: {
                steerToward(angToPlayer, dt);
                forward(ctx, SPEED_MULT, dt);
                break;
            }
            case AIM: {
                steerToward(angToPlayer, dt);
                boolean aligned = Math.abs(signed8(angToPlayer - this.facing)) <= 4;
                if (dist >= FWD_START_DIST_FAR || aligned) {
                    forward(ctx, SPEED_MULT * 0.9f, dt);
                }
                break;
            }
            case WANDER: {
                steerToward(this.turnTo, dt);
                forward(ctx, SPEED_MULT * 0.95f, dt);
                break;
            }
            case SPECIAL: {
                steerToward(this.turnTo, dt);
                forward(ctx, SPEED_MULT, dt);
                break;
            }
        }

        tryShootPlayer(ctx);
    }

    private void choosePlan(GameContext ctx, float dist, int angToPlayer) {
        int JIT = (int) (ctx.nmiCount & 0x03L);
        int RJIT = (int) ((ctx.nmiCount >> 1) & 0x03L);

        if ((ctx.nmiCount & 0x1FL) == 0L) {
            this.reverseFlags |= 0x01 | (MathUtils.randomBoolean() ? 0x02 : 0x00);
            this.moveCounter = REVERSE_TIME_FRAMES + RJIT;
            return;
        }

        int roll = MathUtils.random(0, 255);

        if (dist > 12000f) {
            if (roll < 208) {
                plan = Plan.CHARGE;
                this.turnTo = angToPlayer;
            } else {
                plan = Plan.SERPENTINE;
                serpDuration = NEW_HEADING_FRAMES + JIT + MathUtils.random(8, 20);
            }
            this.moveCounter = NEW_HEADING_FRAMES + JIT;
            return;
        }

        if (dist < 1400f) {
            if (roll < 176) {
                plan = Plan.AIM;
                this.turnTo = angToPlayer;
                this.moveCounter = MathUtils.random(14, 40) + JIT;
            } else {
                plan = Plan.SPECIAL;
                boolean left = MathUtils.randomBoolean();
                this.turnTo = u8(angToPlayer + (left ? -PIVOT_135_STEPS : +PIVOT_135_STEPS));
                this.moveCounter = NEW_HEADING_FRAMES + (JIT << 1);
            }
            return;
        }

        if (roll < 160) {
            plan = Plan.SERPENTINE;
            serpDuration = NEW_HEADING_FRAMES + MathUtils.random(8, 24) + JIT;
            this.moveCounter = serpDuration;
        } else if (roll < 212) {
            plan = Plan.WANDER;
            int off = MathUtils.random(0, MICRO_OFFSET_STEPS_MAX);
            this.turnTo = u8(this.facing + (MathUtils.randomBoolean() ? +off : -off));
            this.moveCounter = NEW_HEADING_FRAMES + JIT;
        } else {
            plan = Plan.AIM;
            this.turnTo = angToPlayer;
            this.moveCounter = MathUtils.random(10, 20) + JIT;
        }
    }

    private void steerToward(int target, float dt) {
        int delta = signed8((this.facing - target) & 0xFF);
        if (delta == 0) {
            return;
        }
        if (delta >= 0) {
            rotateRight(dt);
        } else {
            rotateLeft(dt);
        }
    }
}
