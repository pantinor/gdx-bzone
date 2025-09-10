package bzone;

import static bzone.BattleZone.nearestWrappedPos;
import static bzone.BattleZone.to16;
import static bzone.BattleZone.wrapDelta16;
import static bzone.Tank.ANGLE_STEPS;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;

public class Missile {

    private static final float BASE_SPEED = 3600f;
    private static final float SPEED_RAMP = 1.50f;
    private static final float MAX_SPEED = 12400f;

    private static final float TURN_DEG_PER_SEC = 180f;

    private static final float HOP_HEIGHT = 1200f;
    private static final float HOP_DURATION = 0.50f;
    private static final float HOP_COOLDOWN = 0.10f;
    private static final float MISSILE_RADIUS = 300;

    private static final float GRAVITY = -6000f;
    private float verticalVelocity = 0f;

    private boolean hopping = false;
    private boolean falling = false;
    private float hopPhase = 0f;
    private float hopCooldown = 0f;

    private boolean zigActive = true;
    private int zigFlipsDone = 0;
    private static final int MAX_ZIGZAGS = 2;

    public final GameModelInstance inst;

    public final Vector3 pos = new Vector3();
    private static final Vector3 TMP1 = new Vector3();
    
    private int facing;
    private float speed = BASE_SPEED;
    public boolean active = false;

    public Missile(GameModelInstance inst) {
        this.inst = inst;
    }

    private void applyWrappedTransform(GameContext ctx) {
        float refX16 = to16(ctx.playerX);
        float refZ16 = to16(ctx.playerZ);
        float obX16 = to16(pos.x);
        float obZ16 = to16(pos.z);

        float dx16 = wrapDelta16(obX16 - refX16);
        float dz16 = wrapDelta16(obZ16 - refZ16);

        float wx = ctx.playerX + dx16;
        float wz = ctx.playerZ + dz16;

        inst.transform.idt()
                .translate(wx, pos.y, wz)
                .rotate(Vector3.Y, facing * 360f / ANGLE_STEPS);
    }

    public void spawn(GameContext ctx) {

        ctx.missileCount += 1;

        this.facing = 64;
        this.verticalVelocity = 0f;
        this.falling = (this.pos.y > 0f);
        this.hopping = false;
        this.speed = BASE_SPEED;
        this.hopPhase = 0f;
        this.zigActive = true;
        this.zigFlipsDone = 0;
        this.active = true;

        applyWrappedTransform(ctx);

        Sounds.play(Sounds.Effect.MISSILE_MAX);
    }

    public void render(Camera cam, ModelBatch modelBatch, Environment environment) {
        if (!active) {
            return;
        }

        nearestWrappedPos(this.inst, cam.position.x, cam.position.z, TMP1);
        if (cam.frustum.pointInFrustum(TMP1)) {
            this.inst.transform.val[Matrix4.M03] = TMP1.x;
            this.inst.transform.val[Matrix4.M13] = TMP1.y;
            this.inst.transform.val[Matrix4.M23] = TMP1.z;
            modelBatch.render(this.inst, environment);
        }
    }

    public void update(GameContext ctx, float dt) {
        if (!active) {
            return;
        }

        // Airborne phase: fall straight down; no hop, no horizontal movement, no collision probe.
        if (falling) {
            verticalVelocity += GRAVITY * dt;
            pos.y += verticalVelocity * dt;
            if (pos.y <= 0f) {
                pos.y = 0f;
                verticalVelocity = 0f;
                falling = false;
            }
            applyWrappedTransform(ctx);
            return;
        }

        // Base desired bearing to player
        int desired = calcAngleToPlayer(ctx);

        if (zigActive) {
            final int ZIG_PERIOD_FRAMES = 28;
            final int HALF_PERIOD = ZIG_PERIOD_FRAMES >> 1;
            final int ZIG_ANGLE_STEPS = 12;
            final int MAX_FLIPS = MAX_ZIGZAGS * 2;

            int phase = (int) (ctx.nmiCount % ZIG_PERIOD_FRAMES);
            int sign = (phase < HALF_PERIOD) ? +1 : -1;
            int targetWithOffset = u8(desired + sign * ZIG_ANGLE_STEPS);
            boolean atFlipBoundary = (phase == 0 || phase == HALF_PERIOD);
            if (atFlipBoundary && !hopping) {
                this.facing = targetWithOffset;
                zigFlipsDone++;
                if (zigFlipsDone >= MAX_FLIPS) {
                    zigActive = false;
                }
            } else {
                // While within a side (or while hopping), steer toward the side-offset bearing
                final float TURN_WHILE_ZIG_DEG_PER_SEC = 220f;
                int delta = signed8((targetWithOffset - this.facing) & 0xFF);
                float stepsPerSec = (TURN_WHILE_ZIG_DEG_PER_SEC / 360f) * ANGLE_STEPS;
                int maxStep = Math.max(1, (int) Math.floor(stepsPerSec * dt));
                if (delta > 0) {
                    this.facing = u8(this.facing + Math.min(delta, maxStep));
                } else if (delta < 0) {
                    this.facing = u8(this.facing + Math.max(delta, -maxStep));
                }
            }
        } else {
            // Zig phase finished -> straight homing to player (bounded turn rate)
            int delta = signed8((desired - this.facing) & 0xFF);
            float stepsPerSec = (TURN_DEG_PER_SEC / 360f) * ANGLE_STEPS;
            int maxStep = Math.max(1, (int) Math.floor(stepsPerSec * dt));
            if (delta > 0) {
                this.facing = u8(this.facing + Math.min(delta, maxStep));
            } else if (delta < 0) {
                this.facing = u8(this.facing + Math.max(delta, -maxStep));
            }
        }

        speed = Math.min(MAX_SPEED, speed * SPEED_RAMP);

        // Move forward along current facing
        float rad = (facing & 0xFF) * (MathUtils.PI2 / ANGLE_STEPS);
        float fx = MathUtils.sin(rad);
        float fz = MathUtils.cos(rad);

        float step = speed * dt;
        float dx = fx * step;
        float dz = fz * step;

        if (hopCooldown > 0f) {
            hopCooldown = Math.max(0f, hopCooldown - dt);
        }

        if (!hopping) {
            // Look ahead for a collision; if so, start a hop
            float probeX = pos.x - fx * 3900;
            float probeZ = pos.z - fz * 3900;
            boolean willCollide = ctx.collisionChecker.collides(probeX, probeZ);

            if (willCollide && hopCooldown <= 0f) {
                hopping = true;
                hopPhase = 0f;
                pos.x += fx * Math.min(MISSILE_RADIUS, step * 0.5f);
                pos.z += fz * Math.min(MISSILE_RADIUS, step * 0.5f);
            } else {
                // Move normally
                pos.x += dx;
                pos.z += dz;
            }
        } else {
            // Hop arc
            hopPhase += dt / HOP_DURATION;
            float t = MathUtils.clamp(hopPhase, 0f, 1f);
            float yOffset = MathUtils.sin(t * MathUtils.PI) * HOP_HEIGHT;
            pos.x += dx;
            pos.z += dz;
            pos.y = yOffset;

            if (t >= 1f) {
                hopping = false;
                hopCooldown = HOP_COOLDOWN;
                pos.y = 0f; // ground
            }
        }

        // Proximity kill vs player
        float dx16 = BattleZone.wrapDelta16(BattleZone.to16(this.pos.x) - BattleZone.to16(ctx.playerX));
        float dz16 = BattleZone.wrapDelta16(BattleZone.to16(this.pos.z) - BattleZone.to16(ctx.playerZ));
        if (dx16 * dx16 + dz16 * dz16 <= MISSILE_RADIUS * MISSILE_RADIUS) {
            kill();
            ctx.playerSpawn.spawn();
            return;
        }

        applyWrappedTransform(ctx);
    }

    public void kill() {
        active = false;
        speed = BASE_SPEED;
        Sounds.play(Sounds.Effect.EXPLOSION);
    }

    private int calcAngleToPlayer(GameContext ctx) {
        float dx16 = wrapDelta16(to16(ctx.playerX) - to16(this.pos.x));
        float dz16 = wrapDelta16(to16(ctx.playerZ) - to16(this.pos.z));
        float a = MathUtils.atan2((float) dx16, (float) dz16);
        return ((int) Math.round((a / MathUtils.PI2) * ANGLE_STEPS)) & 0xFF;
    }

    private static int u8(int v) {
        return v & 0xFF;
    }

    private static int signed8(int v) {
        int x = v & 0xFF;
        return (x >= 128) ? (x - 256) : x;
    }
}
