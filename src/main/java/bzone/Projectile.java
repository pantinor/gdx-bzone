package bzone;

import static bzone.BattleZone.nearestWrappedPos;
import static bzone.BattleZone.wrap16f;
import static bzone.BattleZone.wrapDelta16;
import static bzone.BattleZone.to16;

import bzone.EnemyAI.Context;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;
import java.util.List;

public class Projectile {

    private static final Matrix4 MAT1 = new Matrix4();
    private static final Vector3 TMP1 = new Vector3();

    private static final float PROJECTILE_SPAWN_OFFSET = 0.8f;     // how far in front of barrel
    private static final float PROJECTILE_YAW_OFFSET_DEG = 0f;     // set to 90f if mesh "front" is +X
    private static final float PROJECTILE_SPEED = 0.40f;
    private static final int PROJECTILE_TTL_FRAMES = 180;
    private static final float PROJECTILE_RADIUS = 0.15f;
    private static final float PLAYER_HIT_RADIUS = 0.45f;

    private static final float WORLD_Y = 0.5f;

    private final Model model;
    private GameModelInstance inst;
    public boolean active;
    private int ttl;

    private final Vector3 pos = new Vector3();
    private final Vector3 vel = new Vector3();

    public Projectile(Model model) {
        this.model = model;
    }

    public void update(Context enemyCtx, List<GameModelInstance> modelInstances, List<GameModelInstance> obstacles) {
        
        if (!active) {
            return;
        }

        float nextX = pos.x + vel.x;
        float nextZ = pos.z + vel.z;

        float stepLenSq = vel.x * vel.x + vel.z * vel.z;
        int steps = Math.max(1, (int) Math.ceil(Math.sqrt(stepLenSq) / (PROJECTILE_RADIUS * 0.5f)));

        float px = pos.x, pz = pos.z;
        for (int i = 1; i <= steps; i++) {
            float t = i / (float) steps;
            float sx = MathUtils.lerp(px, nextX, t);
            float sz = MathUtils.lerp(pz, nextZ, t);

            if (hitsPlayer(enemyCtx, sx, sz) || projectileHitsWorldXZ(obstacles, sx, sz)) {
                pos.set(sx, WORLD_Y, sz);
                setWrappedToViewer(inst, pos.x, pos.z, enemyCtx.playerX, enemyCtx.playerZ);
                onProjectileHit();
                deactivate(modelInstances);
                return;
            }
        }

        pos.set(nextX, WORLD_Y, nextZ);
        pos.x = wrap16f(pos.x);
        pos.z = wrap16f(pos.z);
        setWrappedToViewer(inst, pos.x, pos.z, enemyCtx.playerX, enemyCtx.playerZ);

        if (--ttl <= 0) {
            deactivate(modelInstances);
        }
    }

    public void spawnFromEnemy(EnemyAI.Enemy enmy, Context enemyCtx, List<GameModelInstance> modelInstances, List<GameModelInstance> obstacles) {
       
        if (active) {
            return;
        }

        float yawDeg = (enmy.facing * 360f) / EnemyAI.ANGLE_STEPS;
        float rad = enmy.facing * MathUtils.PI2 / EnemyAI.ANGLE_STEPS;

        // Velocity in facing direction
        vel.set(MathUtils.sin(rad), 0f, MathUtils.cos(rad)).scl(PROJECTILE_SPEED);

        // Spawn slightly ahead of the muzzle
        pos.set(enmy.pos.x, WORLD_Y, enmy.pos.z).add(
                (vel.x / PROJECTILE_SPEED) * PROJECTILE_SPAWN_OFFSET,
                0f,
                (vel.z / PROJECTILE_SPEED) * PROJECTILE_SPAWN_OFFSET
        );

        pos.x = wrap16f(pos.x);
        pos.z = wrap16f(pos.z);

        ttl = PROJECTILE_TTL_FRAMES;

        inst = new GameModelInstance(model, pos.x, pos.y, pos.z);
        
        inst.transform.idt().setToRotation(Vector3.Y, yawDeg + PROJECTILE_YAW_OFFSET_DEG);
        setWrappedToViewer(inst, pos.x, pos.z, enemyCtx.playerX, enemyCtx.playerZ);

        inst.calculateTransforms();

        if (!modelInstances.contains(inst)) {
            modelInstances.add(inst);
        }

        active = true;

        if (hitsPlayer(enemyCtx, pos.x, pos.z) || projectileHitsWorldXZ(obstacles, pos.x, pos.z)) {
            onProjectileHit();
            deactivate(modelInstances);
        }
    }

    private boolean hitsPlayer(Context enemyCtx, float x, float z) {
        int dx = d16(x, enemyCtx.playerX);
        int dz = d16(z, enemyCtx.playerZ);
        long dist2 = (long) dx * (long) dx + (long) dz * (long) dz;
        long r2 = (long) (PLAYER_HIT_RADIUS * PLAYER_HIT_RADIUS);
        return dist2 <= r2;
    }

    private boolean projectileHitsWorldXZ(List<GameModelInstance> obstacles, float x, float z) {
        for (GameModelInstance ob : obstacles) {
            Vector3 wrapped = nearestWrappedPos(ob, x, z, TMP1);
            if (overlapsInstanceXZAt(ob, wrapped.x, wrapped.z, PROJECTILE_RADIUS)) {
                return true;
            }
        }
        return false;
    }

    private final BoundingBox tmpBox = new BoundingBox();

    private boolean overlapsInstanceXZAt(GameModelInstance inst, float testX, float testZ, float radius) {
        // Build a temporary AABB at (testX, testZ) using the *local* bounds and current rotation
        tmpBox.set(inst.localBounds);
        // Get rotation+scale from the instance’s transform, but override translation with (testX, testZ)
        MAT1.set(inst.transform).setTranslation(testX, WORLD_Y, testZ);
        tmpBox.mul(MAT1);

        float minX = tmpBox.min.x, maxX = tmpBox.max.x;
        float minZ = tmpBox.min.z, maxZ = tmpBox.max.z;

        float cx = MathUtils.clamp(testX, minX, maxX);
        float cz = MathUtils.clamp(testZ, minZ, maxZ);

        float dx = testX - cx, dz = testZ - cz;
        return dx * dx + dz * dz <= radius * radius;
    }

    private void onProjectileHit() {
        // TODO: damage/explosion sound/FX
    }

    private void deactivate(List<GameModelInstance> modelInstances) {
        active = false;
        if (inst != null) {
            modelInstances.remove(inst);
        }
    }

    private static int d16(float a, float b) {
        return wrapDelta16(to16(a) - to16(b));
    }

    private static void setWrappedToViewer(GameModelInstance gi, float x, float z, float viewerX, float viewerZ) {
        int dx = d16(x, viewerX);
        int dz = d16(z, viewerZ);
        gi.transform.setTranslation(viewerX + dx, WORLD_Y, viewerZ + dz);
    }
}
