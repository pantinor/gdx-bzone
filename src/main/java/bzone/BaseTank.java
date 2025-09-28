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

public abstract class BaseTank {

    public static final int ANGLE_STEPS = 256;
    public static final int SPAWN_PROTECTION = 600;

    protected static final int RADAR_SPIN = 4;
    protected static final int CLOSE_FIRING_ANGLE = 16;
    protected static final int REVERSE_TIME_FRAMES = 24;
    protected static final int FORWARD_TIME_FRAMES = 256;
    protected static final int NEW_HEADING_FRAMES = 32;

    protected static final float SUPER_SPEED_MULT = 2.0f;
    protected static final float FWD_SPEED_SLOW = 2800f;
    protected static final float TURN_DEG_PER_SEC = 30f;

    protected static final float FWD_START_DISTANCE_SLOW_TANK = 1280;
    protected static final float FWD_START_DISTANCE_SUPER_TANK = 2048;

    protected GameModelInstance inst;
    protected final GameModelInstance radar;
    protected final Projectile projectile;

    public final Vector3 pos = new Vector3();
    protected final Vector3 savedPos = new Vector3();
    private static final Vector3 TMP1 = new Vector3();

    public boolean alive = false;
    public int facing;                     // 0..255

    protected int radarFacing;                // 0..255
    protected int moveCounter;                // frames left for current plan
    protected int reverseFlags;               // bit0: reversing, bit1: reverse turn dir (0=R,1=L)
    protected int turnTo;                     // target facing (0..255)

    public BaseTank(GameModelInstance inst, GameModelInstance radar, Projectile projectile) {
        this.inst = inst;
        this.radar = radar;
        this.projectile = projectile;
    }

    public void update(GameContext ctx, float dt) {

        if (!this.alive) {
            return;
        }

        if (ctx.spawnProtected != SPAWN_PROTECTION) {
            ctx.spawnProtected = Math.min(SPAWN_PROTECTION, ctx.spawnProtected + 1);
        }

        updateTank(ctx, dt);

        applyWrappedTransform(ctx);
    }

    protected abstract void updateTank(GameContext ctx, float dt);

    public void render(Camera cam, GameContext ctx, ModelBatch modelBatch, Environment environment) {

        if (!this.alive) {
            return;
        }

        nearestWrappedPos(this.inst, cam.position.x, cam.position.z, TMP1);
        if (cam.frustum.pointInFrustum(TMP1)) {
            this.inst.transform.val[Matrix4.M03] = TMP1.x;
            this.inst.transform.val[Matrix4.M13] = TMP1.y;
            this.inst.transform.val[Matrix4.M23] = TMP1.z;
            modelBatch.render(this.inst, environment);
        }

        if (this.radar != null && !ctx.isSuperTank()) {
            nearestWrappedPos(this.radar, cam.position.x, cam.position.z, TMP1);
            if (cam.frustum.pointInFrustum(TMP1)) {
                this.radar.transform.val[Matrix4.M03] = TMP1.x;
                this.radar.transform.val[Matrix4.M13] = TMP1.y;
                this.radar.transform.val[Matrix4.M23] = TMP1.z;
                modelBatch.render(this.radar, environment);
            }
        }
    }

    protected void savePos() {
        savedPos.set(pos);
    }

    protected void restorePos() {
        pos.set(savedPos);
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

        inst.transform.idt()
                .translate(wx, pos.y, wz)
                .rotate(Vector3.Y, facing * 360f / ANGLE_STEPS);

        if (this.radar != null) {
            radar.transform.set(inst.transform)
                    .translate(0, 800, -512)
                    .rotate(Vector3.Y, radarFacing * 360f / ANGLE_STEPS);
        }
    }

    protected void tryShootPlayer(GameContext ctx) {
        if (ctx.spawnProtected < SPAWN_PROTECTION) {
            return;
        }
        if (ctx.playerScore < 2000 && ctx.spawnProtected != SPAWN_PROTECTION) {
            return;
        }
        int diff = Math.abs(signed8(calcAngleToPlayer(ctx) - this.facing));
        if (diff >= 2) {
            return;
        }
        this.projectile.spawnFromTank(this, ctx);
    }

    protected void stepForward(float spd) {
        this.savePos();
        float rad = this.facing * MathUtils.PI2 / ANGLE_STEPS;
        this.pos.x += MathUtils.sin(rad) * spd;
        this.pos.z += MathUtils.cos(rad) * spd;
    }

    protected void forward(GameContext ctx, float mult, float dt) {
        float spd = FWD_SPEED_SLOW * mult * dt;
        stepForward(spd);
        if (ctx.collisionChecker.collides(this.pos.x, this.pos.z)) {
            this.restorePos();
            int dir = (MathUtils.randomBoolean() ? 0x02 : 0x00) | 0x01; // reverse + dir
            this.reverseFlags |= dir;
            this.moveCounter = REVERSE_TIME_FRAMES;
        }
    }

    protected void moveBackward(GameContext ctx, float dt) {
        stepForward(-FWD_SPEED_SLOW * dt);
        if (ctx.collisionChecker.collides(this.pos.x, this.pos.z)) {
            this.restorePos();
            this.reverseFlags &= ~0x01;
            int offset = MathUtils.random(0x10, 0x20);
            if ((this.reverseFlags & 0x02) != 0) {
                this.facing = u8(this.facing + offset);
            } else {
                this.facing = u8(this.facing - offset);
            }
            this.turnTo = this.facing;
            this.moveCounter = NEW_HEADING_FRAMES;
        }
    }

    protected void rotateLeft(float dt) {
        turn(+dt);
    }

    protected void rotateRight(float dt) {
        turn(-dt);
    }

    protected void turn(float dt) {
        float stepsPerSec = (TURN_DEG_PER_SEC / 360f) * ANGLE_STEPS;
        float raw = stepsPerSec * dt;
        int step = Math.max(1, Math.round(Math.abs(raw)));
        this.facing = u8(this.facing + (raw >= 0 ? step : -step));
    }

    protected int calcAngleToPlayer(GameContext ctx) {
        float dx16 = wrapDelta16(to16(ctx.playerX) - to16(this.pos.x));
        float dz16 = wrapDelta16(to16(ctx.playerZ) - to16(this.pos.z));
        float a = MathUtils.atan2((float) dx16, (float) dz16); // +Z is “north”
        return ((int) Math.round((a / MathUtils.PI2) * ANGLE_STEPS)) & 0xFF;
    }

    protected int degToSteps(int deg) {
        return Math.round(deg * (ANGLE_STEPS / 360f));
    }

    protected float distanceWrapped16(float x1, float z1, float x2, float z2) {
        float dx16 = wrapDelta16(to16(x1) - to16(x2));
        float dz16 = wrapDelta16(to16(z1) - to16(z2));
        return (float) Math.sqrt(dx16 * dx16 + dz16 * dz16);
    }

    protected static int u8(int v) {
        return v & 0xFF;
    }

    protected static int signed8(int v) {
        int x = v & 0xFF;
        return (x >= 128) ? (x - 256) : x;
    }
}
