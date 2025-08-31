package bzone;

import static bzone.BattleZone.nearestWrappedPos;
import static bzone.BattleZone.wrap16f;
import static bzone.BattleZone.wrapDelta16;
import static bzone.BattleZone.to16;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;
import java.util.List;

public class Projectile {

    private static final Matrix4 MAT1 = new Matrix4();
    private static final Vector3 TMP1 = new Vector3();

    private static final float PROJECTILE_SPAWN_OFFSET = 0.8f;
    private static final float PROJECTILE_SPEED_PER_SEC = 15000;
    private static final int PROJECTILE_TTL_FRAMES = 180;
    private static final float PLAYER_HIT_RADIUS = 800;

    private static final float WORLD_Y = 0.5f;

    public final GameModelInstance inst;
    public boolean active;
    private int ttl;

    private final Vector3 pos = new Vector3();
    private final Vector3 vel = new Vector3();

    public Projectile(GameModelInstance inst) {
        this.inst = inst;
    }

    public void update(GameContext ctx, List<GameModelInstance> obstacles, float dt) {
        if (!active) {
            return;
        }

        // distance this frame
        float stepX = vel.x * dt;
        float stepZ = vel.z * dt;

        float nextX = pos.x + stepX;
        float nextZ = pos.z + stepZ;

        // standard trick in physics engines for continuous collision detection
        // steps and substeps for collision detection is to avoid a common problem called tunneling 
        // where a fast-moving object "skips through" obstacles between frames
        float moveLenSq = stepX * stepX + stepZ * stepZ;
        int steps = Math.max(1, (int) Math.ceil(Math.sqrt(moveLenSq)));

        float px = pos.x, pz = pos.z;
        for (int i = 1; i <= steps; i++) {
            float t = i / (float) steps;
            float sx = MathUtils.lerp(px, nextX, t);
            float sz = MathUtils.lerp(pz, nextZ, t);

            if (projectileHitsWorldXZ(obstacles, sx, sz)) {
                active = false;
                return;
            }

            if (hitsPlayer(ctx, sx, sz)) {
                onProjectileHit();
                active = false;
                return;
            }
        }

        pos.x = wrap16f(nextX);
        pos.z = wrap16f(nextZ);
        setWrappedToViewer(inst, pos.x, pos.z, ctx.playerX, ctx.playerZ);

        if (--ttl <= 0) {
            active = false;
        }

    }

    public void spawnFromEnemy(EnemyAI.Enemy enmy, GameContext ctx, List<GameModelInstance> obstacles) {

        if (active) {
            return;
        }

        float yawDeg = (enmy.facing * 360f) / EnemyAI.ANGLE_STEPS;
        float rad = enmy.facing * MathUtils.PI2 / EnemyAI.ANGLE_STEPS;

        vel.set(MathUtils.sin(rad), 0f, MathUtils.cos(rad)).scl(PROJECTILE_SPEED_PER_SEC);

        // Spawn slightly ahead of the muzzle
        Vector3 dir = new Vector3(vel).nor();
        pos.set(enmy.pos.x, WORLD_Y, enmy.pos.z).add(
                dir.x * PROJECTILE_SPAWN_OFFSET,
                0f,
                dir.z * PROJECTILE_SPAWN_OFFSET
        );

        pos.x = wrap16f(pos.x);
        pos.z = wrap16f(pos.z);

        ttl = PROJECTILE_TTL_FRAMES;

        inst.transform.idt().setToRotation(Vector3.Y, yawDeg);
        setWrappedToViewer(inst, pos.x, pos.z, ctx.playerX, ctx.playerZ);

        active = true;
    }

    private boolean hitsPlayer(GameContext ctx, float x, float z) {
        float dx = d16(x, ctx.playerX);
        float dz = d16(z, ctx.playerZ);
        long dist2 = (long) dx * (long) dx + (long) dz * (long) dz;
        long r2 = (long) (PLAYER_HIT_RADIUS * PLAYER_HIT_RADIUS);
        return dist2 <= r2;
    }

    private void onProjectileHit() {
        Sounds.play(Sounds.Effect.EXPLOSION);
    }

    private final BoundingBox tmpBox = new BoundingBox();

    private boolean projectileHitsWorldXZ(List<GameModelInstance> obstacles, float projX, float projZ) {
        for (GameModelInstance ob : obstacles) {
            Vector3 wrapped = nearestWrappedPos(ob, projX, projZ, TMP1);

            tmpBox.set(ob.localBounds);
            MAT1.set(ob.transform).setTranslation(wrapped.x, WORLD_Y, wrapped.z);
            tmpBox.mul(MAT1);

            float minX = tmpBox.min.x, maxX = tmpBox.max.x;
            float minZ = tmpBox.min.z, maxZ = tmpBox.max.z;
            float cx = MathUtils.clamp(projX, minX, maxX);
            float cz = MathUtils.clamp(projZ, minZ, maxZ);
            float dx = projX - cx, dz = projZ - cz;

            if (dx * dx + dz * dz <= 0) {
                return true;
            }
        }
        return false;
    }

    private static float d16(float a, float b) {
        return wrapDelta16(to16(a) - to16(b));
    }

    private static void setWrappedToViewer(GameModelInstance inst, float x, float z, float viewerX, float viewerZ) {
        float dx = d16(x, viewerX);
        float dz = d16(z, viewerZ);
        inst.transform.setTranslation(viewerX + dx, WORLD_Y, viewerZ + dz);
        inst.calculateTransforms();
    }
}
