package bzone;

import static bzone.BattleZone.nearestWrappedPos;
import static bzone.BattleZone.to16;
import static bzone.BattleZone.wrapDelta16;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;

public class Saucer {

    private static final float TTL_SECONDS = 17f;
    private static final float ROT_PERIOD_SECONDS = 3f;
    private static final float ROT_SPEED_DEG_PER_SEC = 360f / ROT_PERIOD_SECONDS;
    private static final float COURSE_MIN_SECONDS = 5;
    private static final float COURSE_MAX_SECONDS = 15;
    private static final float SPEED_MIN = 500f;
    private static final float SPEED_MAX = 1200f;

    public final GameModelInstance inst;
    public final Vector3 pos = new Vector3();
    public boolean active = false;
    private float timeToLive;
    private float rotTimer = 0f;
    private float courseTimer = 0f;
    private final Vector3 vel = new Vector3();
    private float spawnCooldown = 0f;

    private static final Vector3 TMP1 = new Vector3();

    public Saucer(GameModelInstance inst) {
        this.inst = inst;
    }

    public void spawn() {
        timeToLive = TTL_SECONDS;
        active = true;
        rotTimer = 0f;
        rollNewCourse();
        Sounds.play(Sounds.Effect.SAUCER_ACTIVE);
    }

    public void kill() {
        active = false;
        Sounds.Effect.SAUCER_ACTIVE.sound().stop();
    }

    public void update(GameContext ctx, float dt) {

        if (!active) {
            if (spawnCooldown > 0f) {
                spawnCooldown -= dt;
            }
            return;
        }

        timeToLive -= dt;
        if (timeToLive <= 0f) {
            kill();
            return;
        }

        courseTimer -= dt;
        if (courseTimer <= 0f) {
            rollNewCourse();
        }
        pos.x += vel.x * dt;
        pos.z += vel.z * dt;

        rotTimer += dt;
        if (rotTimer >= ROT_PERIOD_SECONDS) {
            rotTimer -= ROT_PERIOD_SECONDS;
        }
        float spinDegrees = rotTimer * ROT_SPEED_DEG_PER_SEC;

        applyWrappedTransform(ctx);
        inst.transform.rotate(Vector3.Y, spinDegrees);
    }

    private void rollNewCourse() {
        courseTimer = MathUtils.random(COURSE_MIN_SECONDS, COURSE_MAX_SECONDS);
        float sx = MathUtils.random(SPEED_MIN, SPEED_MAX) * MathUtils.randomSign();
        float sz = MathUtils.random(SPEED_MIN, SPEED_MAX) * MathUtils.randomSign();
        vel.set(sx, 0f, sz);
    }

    public void applyWrappedTransform(GameContext ctx) {

        float refX16 = to16(ctx.playerX);
        float refZ16 = to16(ctx.playerZ);
        float obX16 = to16(pos.x);
        float obZ16 = to16(pos.z);

        float dx16 = wrapDelta16(obX16 - refX16);
        float dz16 = wrapDelta16(obZ16 - refZ16);

        float wx = ctx.playerX + dx16;
        float wz = ctx.playerZ + dz16;

        inst.transform.setToTranslation(wx, pos.y, wz);
    }

    public void render(Camera cam, ModelBatch modelBatch, Environment environment) {
        if (!this.active) {
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
}
