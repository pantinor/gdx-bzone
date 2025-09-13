package bzone;

import static bzone.BattleZone.WORLD_WRAP_HALF_16BIT;
import static bzone.BattleZone.to16;
import static bzone.BattleZone.wrapDelta16;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;

public class Skimmer extends BaseTank {

    final GameModelInstance skimmer, stinger;

    private static final float SPEED_WANDER = 1.34f;
    private static final float SPEED_PURSUIT = 1.48f;
    private static final float SPEED_DIVE = 1.58f;

    private static final int MAX_TURN_RATE_STEPS = 3;  // max |turnVel| per frame
    private static final int TURN_ACCEL_STEPS = 1;  // how quickly turnVel ramps
    private int turnVelSteps = 0;                        // signed; >0 rotates right, <0 rotates left

    private static final int MICRO_TURN_MAX = 16;
    private static final float DIVE_RANGE = 12000f;
    private static final float RETREAT_RANGE = 2000f;

    private float alt = ALT_HOVER;
    private float targetAlt = ALT_HOVER;
    private static final float ALT_HOVER = 1500f;
    private static final float ALT_DIVE = 500f;
    private static final float ALT_RATE_UP = 1200f;  // units/sec
    private static final float ALT_RATE_DN = 2000f;  // units/sec

    private enum Plan {
        RETREAT, PURSUE, DIVE
    }
    private Plan plan = Plan.PURSUE;

    public Skimmer(Projectile projectile) {
        super(null, null, projectile);
        this.skimmer = Models.buildWireframeInstance(Models.Mesh.SKIMMER, Color.GREEN, 1);
        this.stinger = Models.buildWireframeInstance(Models.Mesh.STINGER, Color.GREEN, 1);
        this.facing = MathUtils.random(0, ANGLE_STEPS - 1);
        this.radarFacing = this.facing;
        this.turnTo = this.facing;
        this.moveCounter = 1;
    }

    @Override
    protected void updateTank(GameContext ctx, float dt) {
        boolean stinger = ctx.playerScore >= 30000;

        if (stinger) {
            this.inst = this.stinger;
        } else {
            this.inst = this.skimmer;
        }

        if (this.moveCounter > 0) {
            this.moveCounter--;
        }

        this.reverseFlags = 0;

        float dx16 = wrapDelta16(to16(ctx.playerX) - to16(this.pos.x));
        float dz16 = wrapDelta16(to16(ctx.playerZ) - to16(this.pos.z));
        float dist = (float) Math.sqrt(dx16 * dx16 + dz16 * dz16);
        int angToPlayer = calcAngleToPlayer(ctx);

        if ((ctx.nmiCount & 0x1FL) == 0L) {
            //System.out.printf("dist=%5.0f plan=%-6s alt=%4.0f angToPlayer=%d mc=%d%n", dist, plan, alt, angToPlayer, moveCounter);
        }

        if (this.moveCounter == 0) {
            choosePlan(ctx, dist, angToPlayer);
        }

        bankToward(this.turnTo, dt);

        switch (plan) {
            case DIVE:
                tryShootPlayer(ctx);
                forward(ctx, stinger ? SPEED_DIVE + .2f : SPEED_DIVE, dt);
                tryShootPlayer(ctx);
                break;
            case PURSUE:
                forward(ctx, stinger ? SPEED_PURSUIT + .2f : SPEED_PURSUIT, dt);
                break;
            default:
                forward(ctx, stinger ? SPEED_WANDER + .2f : SPEED_WANDER, dt);
                break;
        }

        updateAltitude(dt);
        applyAltitude();
    }

    @Override
    protected void tryShootPlayer(GameContext ctx) {
        if (ctx.spawnProtected < SPAWN_PROTECTION) {
            return;
        }
        if (ctx.playerScore < 2000 && ctx.spawnProtected != SPAWN_PROTECTION) {
            return;
        }
        int diff = Math.abs(signed8(calcAngleToPlayer(ctx) - this.facing));
        if (diff >= 4 && alt == 700) {
            return;
        }
        this.projectile.spawnFromTank(this, ctx);
    }

    @Override
    public void forward(GameContext ctx, float mult, float dt) {
        float spd = FWD_SPEED_SLOW * mult * dt;
        stepForward(spd);
    }

    private void choosePlan(GameContext ctx, float dist, int angToPlayer) {

        //System.out.printf("dist=%5.0f plan=%-6s alt=%4.0f angToPlayer=%d mc=%d%n", dist, plan, alt, angToPlayer, moveCounter);

        //slightly vary replan timing by 0–3 frames
        final int JIT = (int) (ctx.nmiCount & 0x03L);// 0,1,2,3 repeating each frame

        if (plan == Plan.RETREAT) {
            float ax = Math.abs(wrapDelta16(to16(ctx.playerX) - to16(this.pos.x)));
            float az = Math.abs(wrapDelta16(to16(ctx.playerZ) - to16(this.pos.z)));
            boolean atEdge = (ax >= WORLD_WRAP_HALF_16BIT - 1000) || (az >= WORLD_WRAP_HALF_16BIT - 1000);
            if (atEdge) {
                //directly back to player
                plan = Plan.PURSUE;
                this.turnTo = angToPlayer;
                targetAlt = ALT_HOVER;
                this.moveCounter = NEW_HEADING_FRAMES + JIT;
                return;
            }

            //serpentine path away from player
            int awayFromPlayerAngle = u8(angToPlayer + 128); // 180° from player
            int wobbleAngle = MathUtils.random(0, MICRO_TURN_MAX);
            this.turnTo = u8(MathUtils.randomBoolean() ? awayFromPlayerAngle + wobbleAngle : awayFromPlayerAngle - wobbleAngle);
            targetAlt = ALT_HOVER;
            this.moveCounter = 8 + JIT;
            return;
        }

        if (plan == Plan.PURSUE && dist >= RETREAT_RANGE && dist <= DIVE_RANGE) {
            plan = Plan.DIVE;
            this.turnTo = u8(angToPlayer);
            targetAlt = ALT_DIVE;
            this.moveCounter = 10 + JIT;
            return;
        }

        if (plan == Plan.DIVE) {
            if (alt >= ALT_DIVE && dist > 1000) {
                //directly back to player
                this.turnTo = u8(angToPlayer);
                targetAlt = ALT_DIVE;
                this.moveCounter = 10 + JIT;
            } else {
                plan = Plan.RETREAT;
                int away = u8(angToPlayer + 128);
                int wobble = MathUtils.random(0, MICRO_TURN_MAX);
                this.turnTo = u8(MathUtils.randomBoolean() ? away + wobble : away - wobble);
                targetAlt = ALT_HOVER;
                this.moveCounter = NEW_HEADING_FRAMES + JIT;
            }
        }

    }

    /**
     * Banked turn with turn velocity + acceleration; cannot stop-and-turn.
     */
    private void bankToward(int target, float dt) {

        int delta = signed8((this.facing - target) & 0xFF);
        int DEAD_BAND_STEPS = 2;

        if (Math.abs(delta) <= DEAD_BAND_STEPS) {
            this.facing = target;
            this.turnVelSteps = 0;
            return;
        }

        // Proportional desired speed: smaller as we approach
        // kp = 0.5 here (delta/2), clamp to max; integer arithmetic keeps it simple
        int desired = delta / 2; // toward 0 as delta shrinks
        if (desired == 0) {
            desired = (delta > 0 ? +1 : -1); // ensure some motion if outside deadband
        }
        desired = Math.max(-MAX_TURN_RATE_STEPS, Math.min(MAX_TURN_RATE_STEPS, desired));

        // Accelerate turnVel toward desired
        if (turnVelSteps < desired) {
            turnVelSteps = Math.min(desired, turnVelSteps + TURN_ACCEL_STEPS);
        }
        if (turnVelSteps > desired) {
            turnVelSteps = Math.max(desired, turnVelSteps - TURN_ACCEL_STEPS);
        }

        // Apply rotation: turnVelSteps >0 means rotate RIGHT, <0 means LEFT
        int steps = Math.abs(turnVelSteps);
        for (int i = 0; i < steps; i++) {
            if (turnVelSteps > 0) {
                rotateRight(dt);
            } else {
                rotateLeft(dt);
            }
        }
    }

    private void updateAltitude(float dt) {
        if (alt < targetAlt) {
            alt = Math.min(targetAlt, alt + ALT_RATE_UP * dt);
        } else if (alt > targetAlt) {
            alt = Math.max(targetAlt, alt - ALT_RATE_DN * dt);
        }
    }

    private void applyAltitude() {
        this.pos.y = alt;
    }

}
