package bzone;

import static bzone.BattleZone.to16;
import static bzone.BattleZone.wrapDelta16;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;

/**
 * Skimmer — ROM-inspired class-3 flier with smooth, banked turning.
 *
 * Key constraints (as requested): - **No reversing** (it’s flying). - **Ignores
 * obstacle collisions** (glides over terrain/objects). - **Cannot
 * stop-and-turn** like a tank: always in motion with smooth turns.
 *
 * ROM-flavored patterns: p0 WANDER : random glide with occasional small turns
 * [class-3] p1 PURSUE : glide toward the player [class-3] p2 DIVE : descend
 * toward ground briefly, then climb [class-3]
 */
public class Skimmer extends BaseTank {

    final GameModelInstance skimmer, stinger;

    private static final float SPEED_WANDER = 1.34f;
    private static final float SPEED_PURSUIT = 1.48f;
    private static final float SPEED_DIVE = 1.58f;

    private static final int MAX_TURN_RATE_STEPS = 3;  // max |turnVel| per frame
    private static final int TURN_ACCEL_STEPS = 1;  // how quickly turnVel ramps
    private int turnVelSteps = 0;                        // signed; >0 rotates right, <0 rotates left

    private static final int MICRO_TURN_MAX = 10;   // ~14°
    private static final int ORBIT_OFF_STEPS = 64;   // 90° ring offset

    private static final float DIVE_MAX_RANGE = 10000f;
    private static final float DIVE_MIN_RANGE = 5000f;
    private static final float RING_CENTER = 1000f;
    private static final float RING_TOLERANCE = 2000f;

    private float alt = ALT_HOVER;
    private float targetAlt = ALT_HOVER;
    private static final float ALT_HOVER = 1500f;
    private static final float ALT_DIVE = 700f;
    private static final float ALT_RATE_UP = 1200f;  // units/sec
    private static final float ALT_RATE_DN = 2000f;  // units/sec

    private enum Plan {
        WANDER, PURSUE, DIVE
    }
    private Plan plan = Plan.WANDER;
    private int orbitDir = MathUtils.randomBoolean() ? +1 : -1;

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
            //System.out.printf("dist=%5.0f plan=%-6s alt=%4.0f -> %4.0f mc=%d%n", dist, plan, alt, targetAlt, moveCounter);
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
                tryShootPlayer(ctx);
                forward(ctx, stinger ? SPEED_PURSUIT + .2f : SPEED_PURSUIT, dt);
                tryShootPlayer(ctx);
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
        int JIT = (int) (ctx.nmiCount & 0x03L);

        // Keep DIVE sticky until we reach the deck
        if (plan == Plan.DIVE && alt > ALT_DIVE + 1f) {
            int away = u8(angToPlayer + (orbitDir > 0 ? -ORBIT_OFF_STEPS : +ORBIT_OFF_STEPS));
            this.turnTo = away;
            targetAlt = ALT_DIVE;
            this.moveCounter = 10 + JIT; // short refresh so we recheck soon
            return;
        }

        int roll = MathUtils.random(0, 255);

        if ((ctx.nmiCount & 0x1FL) == 0L) {
            orbitDir = -orbitDir;
        }

        if (dist > DIVE_MAX_RANGE) {
            plan = Plan.PURSUE; // long glide toward player
            if (roll < 64) {
                int off = MathUtils.random(0, MICRO_TURN_MAX);
                this.turnTo = u8(angToPlayer + (MathUtils.randomBoolean() ? +off : -off));
            } else {
                this.turnTo = angToPlayer;
            }
            this.moveCounter = NEW_HEADING_FRAMES + 8 + JIT;
            return;
        }

        if (dist >= DIVE_MIN_RANGE && dist <= DIVE_MAX_RANGE) {
            plan = Plan.DIVE;
            int away = u8(angToPlayer + (orbitDir > 0 ? -ORBIT_OFF_STEPS : +ORBIT_OFF_STEPS));
            this.turnTo = away;
            targetAlt = ALT_DIVE;
            this.moveCounter = 10 + JIT;
            return;
        }

        boolean onRing = Math.abs(dist - RING_CENTER) <= RING_TOLERANCE;
        if (onRing && roll < 176) {
            plan = Plan.WANDER;
            int wobble = MathUtils.random(0, MICRO_TURN_MAX);
            int base = u8(angToPlayer + orbitDir * ORBIT_OFF_STEPS);
            this.turnTo = u8(MathUtils.randomBoolean() ? base + wobble : base - wobble);
            targetAlt = ALT_HOVER;
            this.moveCounter = NEW_HEADING_FRAMES + JIT;
        } else {
            plan = Plan.PURSUE;
            this.turnTo = angToPlayer;
            targetAlt = ALT_HOVER;
            this.moveCounter = NEW_HEADING_FRAMES + JIT;
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
