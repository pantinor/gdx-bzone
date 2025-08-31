package bzone;

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
    private static final float SPEED_RAMP = 1.20f;
    private static final float MAX_SPEED = 6400f;
    private static final float TURN_DEG_PER_SEC = 180f;

    private static final float HOP_AMPLITUDE = 180f;
    private static final float HOP_FREQ_HZ = 1.5f;

    private static final float HIT_RADIUS = 800;

    private final GameModelInstance inst;

    public final Vector3 pos = new Vector3();

    private int facing;
    private float speed = BASE_SPEED;
    private float hopPhase = 0f;
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
                .translate(wx, pos.y + hopOffset(), wz)
                .rotate(Vector3.Y, facing * 360f / ANGLE_STEPS);
    }

    private float hopOffset() {
        return MathUtils.sin(hopPhase) * HOP_AMPLITUDE;
    }

    public void kill() {
        active = false;
        speed = BASE_SPEED;
        Sounds.play(Sounds.Effect.EXPLOSION);
    }

    public void spawn(GameContext ctx) {

        int R = 0x6000;
        int angSteps = MathUtils.round((ctx.hdFromCam / 360f) * ANGLE_STEPS) & 0xFF;
        float rad = angSteps * MathUtils.PI2 / ANGLE_STEPS;

        float offX = MathUtils.sin(rad) * R;
        float offZ = MathUtils.cos(rad) * R;

        this.pos.set(
                wrap16f(ctx.playerX + offX),
                0f,
                wrap16f(ctx.playerZ + offZ)
        );

        this.facing = 64;
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

        hopPhase += MathUtils.PI2 * HOP_FREQ_HZ * Math.max(0.0001f, dt);
        if (hopPhase > MathUtils.PI2) {
            hopPhase -= MathUtils.PI2;
        }

        int desired = calcAngleToPlayer(ctx);
        int delta = signed8((desired - this.facing) & 0xFF);

        float stepsPerSec = (TURN_DEG_PER_SEC / 360f) * ANGLE_STEPS;
        int maxStep = Math.max(1, (int) Math.floor(stepsPerSec * dt));
        if (delta > 0) {
            this.facing = u8(this.facing + Math.min(delta, maxStep));
        } else if (delta < 0) {
            this.facing = u8(this.facing + Math.max(delta, -maxStep));
        }

        int absDelta = Math.abs(delta);
        if (absDelta <= 12) { // ~17°
            speed = Math.min(MAX_SPEED, speed * SPEED_RAMP);
        } else if (absDelta >= 64) { // 90° off, dampen a bit
            speed = Math.max(BASE_SPEED, speed * 0.95f);
        }

        float rad = this.facing * MathUtils.PI2 / ANGLE_STEPS;
        float stepX = MathUtils.sin(rad) * speed * dt;
        float stepZ = MathUtils.cos(rad) * speed * dt;

        pos.add(stepX, 0f, stepZ);

        float dx16 = wrapDelta16(to16(this.pos.x) - to16(ctx.playerX));
        float dz16 = wrapDelta16(to16(this.pos.z) - to16(ctx.playerZ));

        if (dx16 * dx16 + dz16 * dz16 <= HIT_RADIUS * HIT_RADIUS) {
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
