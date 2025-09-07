package bzone;

import static bzone.BattleZone.to16;
import static bzone.BattleZone.wrapDelta16;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.math.Vector3;

public class Saucer {

    private static final float TTL_SECONDS = 17f;
    private static final float ROT_PERIOD_SECONDS = 3f;
    private static final float ROT_SPEED_DEG_PER_SEC = 360f / ROT_PERIOD_SECONDS;

    public final GameModelInstance inst;
    public final Vector3 pos = new Vector3();
    public boolean active = false;
    private float timeToLive;
    private float rotTimer = 0f;

    public Saucer(GameModelInstance inst) {
        this.inst = inst;
    }

    public void spawn() {
        timeToLive = TTL_SECONDS;
        active = true;
        rotTimer = 0f;
        Sounds.play(Sounds.Effect.SAUCER_ACTIVE);
    }

    public void update(GameContext ctx, float dt) {

        if (!this.active) {
            return;
        }

        timeToLive -= dt;
        if (timeToLive <= 0f) {
            active = false;
            return;
        }

        rotTimer += dt;
        if (rotTimer >= ROT_PERIOD_SECONDS) {
            rotTimer -= ROT_PERIOD_SECONDS;
        }
        float yawDeg = rotTimer * ROT_SPEED_DEG_PER_SEC;

        applyWrappedTransform(ctx);
        
        inst.transform.rotate(Vector3.Y, yawDeg);
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

    public void render(ModelBatch modelBatch, Environment environment) {
        if (!this.active) {
            return;
        }
        modelBatch.render(this.inst, environment);
    }
}
