package bzone;

import static bzone.BattleZone.WORLD_WRAP_HALF_16BIT;
import static bzone.BattleZone.to16;
import static bzone.BattleZone.wrap16f;
import static bzone.BattleZone.wrapDelta16;
import static bzone.Tank.ANGLE_STEPS;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.math.MathUtils;
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

    private final GameModelInstance inst;

    public final Vector3 pos = new Vector3();

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

    public void kill() {
        active = false;
        speed = BASE_SPEED;
        Sounds.play(Sounds.Effect.EXPLOSION);
    }

    public void spawn(GameContext ctx) {

        int start = WORLD_WRAP_HALF_16BIT - 4000;

        int angSteps = MathUtils.round((ctx.hdFromCam / 360f) * ANGLE_STEPS) & 0xFF;
        float rad = angSteps * MathUtils.PI2 / ANGLE_STEPS;

        float offX = MathUtils.sin(rad) * start;
        float offZ = MathUtils.cos(rad) * start;

        this.pos.set(
                wrap16f(ctx.playerX + offX),
                6000f,
                wrap16f(ctx.playerZ + offZ)
        );

        this.facing = 64;
        this.verticalVelocity = 0f;
        this.falling = (this.pos.y > 0f);
        this.hopping = false;
        this.speed = BASE_SPEED;
        this.hopPhase = 0f;
        this.active = true;

        applyWrappedTransform(ctx);

        Sounds.play(Sounds.Effect.MISSILE_MAX);
    }

    public void render(ModelBatch modelBatch, Environment environment) {
        if (!active) {
            return;
        }
        modelBatch.render(this.inst, environment);
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

        // turn toward player
        int desired = calcAngleToPlayer(ctx);
        int delta = signed8((desired - this.facing) & 0xFF);

        float stepsPerSec = (TURN_DEG_PER_SEC / 360f) * ANGLE_STEPS;
        int maxStep = Math.max(1, (int) Math.floor(stepsPerSec * dt));
        if (delta > 0) {
            this.facing = u8(this.facing + Math.min(delta, maxStep));
        } else if (delta < 0) {
            this.facing = u8(this.facing + Math.max(delta, -maxStep));
        }

        speed = Math.min(MAX_SPEED, speed * SPEED_RAMP);

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
            // look ahead for a collision
            float probeX = pos.x - fx * 3900;
            float probeZ = pos.z - fz * 3900;
            boolean willCollide = ctx.collisionChecker.collides(probeX, probeZ);

            if (willCollide && hopCooldown <= 0f) {
                // start a hop
                hopping = true;
                hopPhase = 0f;
                pos.x += fx * Math.min(MISSILE_RADIUS, step * 0.5f);
                pos.z += fz * Math.min(MISSILE_RADIUS, step * 0.5f);
            } else {
                // move normally
                pos.x += dx;
                pos.z += dz;
            }
        } else if (hopping) {
            // hop arc
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

        float dx16 = wrapDelta16(to16(this.pos.x) - to16(ctx.playerX));
        float dz16 = wrapDelta16(to16(this.pos.z) - to16(ctx.playerZ));
        if (dx16 * dx16 + dz16 * dz16 <= MISSILE_RADIUS * MISSILE_RADIUS) {
            kill();
            return;
        }

        applyWrappedTransform(ctx);
    }

    private int calcAngleToPlayer(GameContext ctx) {
        float dx16 = wrapDelta16(to16(ctx.playerX) - to16(this.pos.x));
        float dz16 = wrapDelta16(to16(ctx.playerZ) - to16(this.pos.z));
        float a = MathUtils.atan2((float) dx16, (float) dz16); // +Z is “north”
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
