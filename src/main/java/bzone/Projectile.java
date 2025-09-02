package bzone;

import static bzone.BattleZone.wrap16f;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import java.util.List;

public class Projectile {

    private static final float PROJECTILE_SPAWN_OFFSET = 140f;
    private static final float PROJECTILE_SPEED_PER_SEC = 15000f;
    private static final float PLAYER_HIT_RADIUS = 800f;
    private static final float WORLD_Y = 0.5f;
    private static final float TTL_SECONDS = 3f;

    public final GameModelInstance inst;

    private static final Vector3 TMP1 = new Vector3();

    public boolean active;
    private float timeToLive;
    private float x, z;
    private float dx, dz;

    public Projectile(GameModelInstance inst) {
        this.inst = inst;
    }

    private void spawn(float px, float pz, float yawRadians) {
        dx = (float) Math.sin(yawRadians);
        dz = (float) Math.cos(yawRadians);
        float len = (float) Math.hypot(dx, dz);
        if (len == 0f) {
            dx = 0f;
            dz = 1f;
        } else {
            dx /= len;
            dz /= len;
        }

        float spawnStep = PROJECTILE_SPAWN_OFFSET;

        x = px + dx * spawnStep;
        z = pz + dz * spawnStep;
        
        timeToLive = TTL_SECONDS;
        active = true;

        applyTransform();
        Sounds.play(Sounds.Effect.FIRE);
    }

    public void spawnFromPlayer(GameContext ctx, List<GameModelInstance> obstacles) {
        if (active) {
            return;
        }
        float yawRad = ctx.hdFromCam * MathUtils.degreesToRadians;
        spawn(wrap16f(ctx.playerX), wrap16f(ctx.playerZ), yawRad);
    }

    public void spawnFromTank(Tank tank, GameContext ctx, List<GameModelInstance> obstacles) {
        if (active) {
            return;
        }
        float yawRad = (tank.facing * MathUtils.PI2) / Tank.ANGLE_STEPS;
        spawn(wrap16f(tank.pos.x), wrap16f(tank.pos.z), yawRad);
    }

    public void update(GameContext ctx, List<GameModelInstance> obstacles, float dtSec, boolean fromPlayer) {
        if (!active) {
            return;
        }

        timeToLive -= dtSec;
        if (timeToLive <= 0f) {
            kill(Sounds.Effect.ERROR);
            return;
        }

        float moveDist = PROJECTILE_SPEED_PER_SEC * dtSec;
        if (moveDist <= 0f) {
            applyTransform();
            return;
        }

        float nx = x + dx * moveDist;
        float nz = z + dz * moveDist;

        x = nx;
        z = nz;
        applyTransform();

        if (ctx.collisionChecker.collides(x, z)) {
            kill(Sounds.Effect.BUMP);
            return;
        }

        if (!fromPlayer && hitsPlayer(ctx, x, z, PLAYER_HIT_RADIUS)) {
            kill(Sounds.Effect.EXPLOSION);
            return;
        }

    }

    private static boolean hitsPlayer(GameContext ctx, float projX, float projZ, float radius) {
        float dx = projX - ctx.playerX;
        float dz = projZ - ctx.playerZ;
        return (dx * dx + dz * dz) <= radius * radius;
    }

    private void applyTransform() {
        float yawDeg = MathUtils.atan2(dx, dz) * MathUtils.radiansToDegrees;
        inst.transform.idt().setToRotation(Vector3.Y, yawDeg).setTranslation(x, WORLD_Y, z);
        inst.calculateTransforms();
    }

    private void kill(Sounds.Effect sfx) {
        active = false;
        Sounds.play(sfx);
    }
}
