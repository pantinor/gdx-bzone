package bzone;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL30;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector3;
import java.util.ArrayList;
import java.util.List;
import com.badlogic.gdx.math.Matrix4;
import java.util.concurrent.ThreadLocalRandom;

public class BattleZone implements ApplicationListener, InputProcessor {

    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration cfg = new Lwjgl3ApplicationConfiguration();
        cfg.setTitle("BAttle Zone");
        cfg.setWindowedMode(SCREEN_WIDTH, SCREEN_HEIGHT);
        new Lwjgl3Application(new BattleZone(), cfg);
    }

    public static final int SCREEN_WIDTH = 640 * 2;
    public static final int SCREEN_HEIGHT = 480 * 2;

    private static final Vector3 TMP1 = new Vector3();
    private static final Matrix4 MAT1 = new Matrix4();

    /**
     * Size of the toroidal world in the original 16-bit ROM coordinate space.
     * Positions wrap modulo this value (i.e., x,z ∈ [0, 65536)) to emulate the
     * hardware’s unsigned 16-bit addressing of the playfield.
     */
    public static final int WORLD_WRAP_16BIT = 1 << 16; // 65536

    /**
     * Half of {@link #WORLD_WRAP_16BIT}. Used to bring deltas into the signed
     * range [-32768, +32767] so we can choose the nearest wrapped “image” of an
     * object when computing distances or offsets.
     */
    public static final int WORLD_WRAP_HALF_16BIT = WORLD_WRAP_16BIT >>> 1; // 32768

    private static final float ENEMY_COLLISION_RADIUS = 1f; // tune to match tank size

    private boolean wDown, aDown, sDown, dDown;
    private final float yawSpeedDeg = 90f;
    private final float moveSpeed = 3200f;
    private float headingDeg = 0f;

    private SpriteBatch batch;
    private ModelBatch modelBatch;
    private PerspectiveCamera cam;
    private OrthographicCamera backGroundCam;
    private Environment environment;
    private final List<GameModelInstance> obstacles = new ArrayList<>(21);
    private ShapeRenderer sr;
    private final Background background = new Background();

    private final GameContext context = new GameContext();
    private int nmiCount = 0;

    //private Missile projectile;
    private Tank tank;
    private Projectile projectile;

    private final Radar radarScreen = new Radar();
    private EngineSound engine;

    BitmapFont font;

    @Override
    public void create() {

        FreeTypeFontGenerator generator = new FreeTypeFontGenerator(Gdx.files.classpath("assets/data/bzone-font.ttf"));
        FreeTypeFontGenerator.FreeTypeFontParameter parameter = new FreeTypeFontGenerator.FreeTypeFontParameter();

        parameter.size = 18;
        parameter.color = Color.RED;
        parameter.hinting = FreeTypeFontGenerator.Hinting.Full;
        font = generator.generateFont(parameter);

        batch = new SpriteBatch();

        backGroundCam = new OrthographicCamera();
        backGroundCam.setToOrtho(false, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        backGroundCam.update();

        cam = new PerspectiveCamera(67, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        cam.near = 1f;
        cam.far = 60000f;
        cam.update();

        sr = new ShapeRenderer();

        Gdx.input.setInputProcessor(this);

        environment = new Environment();
        this.environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.05f, 0.05f, 0.05f, 1f));

        modelBatch = new ModelBatch();

        GameModelInstance tank = Models.buildWireframeInstance(Models.Mesh.SLOW_TANK.wf(), Color.RED, 1, -1f, 3f, 0.5f, 3f);
        GameModelInstance radar = Models.buildWireframeInstance(Models.Mesh.RADAR1.wf(), Color.RED, 1, -1f, 3f, 0.5f, 3f);
        GameModelInstance missile = Models.buildWireframeInstance(Models.Mesh.MISSILE.wf(), Color.WHITE, 1, -1f, 3f, 0.5f, 3f);

        this.tank = new Tank(tank, radar);

        context.collisionChecker = this::collidesAnyModelXZ;

        randomSpawn(cam.position, context);
        randomSpawn(this.tank.pos, context);

        headingDeg = rand8();//0 is facing the moon

        GameModelInstance projectileInstance = Models.buildWireframeInstance(Models.Mesh.PROJECTILE.wf(), Color.YELLOW, 1, -1f, 0f, 0.5f, 0f);
        projectile = new Projectile(projectileInstance);
        context.shooter = () -> projectile.spawnFromEnemy(this.tank, context, obstacles);

        loadMapObstacles();

        engine = new EngineSound();
        engine.setIdleClockHz(240f);
        engine.setMaxClockHz(1200f);
        engine.start();
    }

    @Override
    public void render() {

        float dt = Gdx.graphics.getDeltaTime();

        float yaw = 0f;
        if (aDown) {
            yaw += yawSpeedDeg * dt;
        }
        if (dDown) {
            yaw -= yawSpeedDeg * dt;
        }
        if (yaw != 0f) {
            cam.rotate(Vector3.Y, yaw);
            headingDeg = (headingDeg + yaw) % 360f;
        }

        float move = 0f;
        if (wDown) {
            move += moveSpeed * dt;
        }
        if (sDown) {
            move -= moveSpeed * dt;
        }
        if (move != 0f) {
            TMP1.set(cam.direction.x, 0f, cam.direction.z).nor().scl(move);
            cam.position.add(TMP1);
            cam.position.x = wrap16f(cam.position.x);
            cam.position.z = wrap16f(cam.position.z);
        }

        cam.update(true);

        context.playerX = cam.position.x;
        context.playerZ = cam.position.z;
        context.nmiCount = ++this.nmiCount;

        projectile.update(context, obstacles, dt);
        tank.update(context, dt);
        context.projectileBusy = projectile.active;
        engine.update(dt);

        Gdx.gl.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        Gdx.gl.glClear(GL30.GL_COLOR_BUFFER_BIT | GL30.GL_DEPTH_BUFFER_BIT);

        modelBatch.begin(cam);

        drawObstacles(modelBatch);

        if (context.projectileBusy) {
            modelBatch.render(projectile.inst, environment);
        }

        tank.render(modelBatch, environment);

        modelBatch.end();

        backGroundCam.setToOrtho(false, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        backGroundCam.update();

        sr.setProjectionMatrix(backGroundCam.combined);

        float hd = (headingDeg % 360f + 360f) % 360f;

        background.drawBackground2D(sr, hd);

        drawHUD(dt);

        batch.begin();
        font.draw(batch, "pos = " + cam.position.x + " " + cam.position.z, 100, SCREEN_HEIGHT - 100);
        batch.end();
    }

    @Override
    public boolean keyDown(int keycode) {
        switch (keycode) {
            case Input.Keys.W:
                wDown = true;
                engine.setThrottle(1f);
                return true;
            case Input.Keys.A:
                aDown = true;
                return true;
            case Input.Keys.S:
                sDown = true;
                engine.setThrottle(1f);
                return true;
            case Input.Keys.D:
                dDown = true;
                return true;

            case Input.Keys.NUM_1:
                context.playerScore = 1;
                return true;
            case Input.Keys.NUM_2:
                context.playerScore = -1;
                return true;
            case Input.Keys.NUM_3:
                context.playerScore = 0;
                return true;
            case Input.Keys.NUM_4:
                return true;
            case Input.Keys.NUM_5:
                return true;
            case Input.Keys.NUM_6:
                return true;
            case Input.Keys.NUM_7:
                return true;
            case Input.Keys.NUM_8:
                return true;

            default:
                return false;
        }
    }

    @Override
    public boolean keyUp(int keycode) {
        switch (keycode) {
            case Input.Keys.W:
                wDown = false;
                engine.setThrottle(0f);
                return true;
            case Input.Keys.A:
                aDown = false;
                return true;
            case Input.Keys.S:
                sDown = false;
                engine.setThrottle(0f);
                return true;
            case Input.Keys.D:
                dDown = false;
                return true;
            default:
                return false;
        }
    }

    @Override
    public boolean keyTyped(char character) {
        return false;
    }

    @Override
    public boolean touchDown(int x, int y, int pointer, int button) {
        return false;
    }

    @Override
    public boolean touchUp(int x, int y, int pointer, int button) {
        return false;
    }

    @Override
    public boolean touchDragged(int x, int y, int pointer) {
        return false;
    }

    @Override
    public boolean mouseMoved(int screenX, int screenY) {
        return false;
    }

    @Override
    public void pause() {
    }

    @Override
    public void resume() {
    }

    @Override
    public void dispose() {
        modelBatch.dispose();
    }

    @Override
    public void resize(int width, int height) {
        backGroundCam.setToOrtho(false, width, height);
        backGroundCam.update();
    }

    @Override
    public boolean touchCancelled(int i, int i1, int i2, int i3) {
        return false;
    }

    @Override
    public boolean scrolled(float f, float f1) {
        return false;
    }

    private void drawHUD(float dt) {
        float reticleIntensity = 0.5f;

        Gdx.gl.glLineWidth(2);
        sr.begin(ShapeRenderer.ShapeType.Line);
        sr.setColor(0f, reticleIntensity, 0f, 1f);

        //reticle
        sr.line(640 - 50, 480 + 25, 640 - 50, 480 + 25 + 25);
        sr.line(640 + 50, 480 + 25, 640 + 50, 480 + 25 + 25);
        sr.line(640, 480 + 50, 640, 480 + 50 + 50);
        sr.line(640 - 50, 480 + 50, 640 + 50, 480 + 50);

        sr.line(640 - 50, 480 - 25, 640 - 50, 480 - 25 - 25);
        sr.line(640 + 50, 480 - 25, 640 + 50, 480 - 25 - 25);
        sr.line(640, 480 - 50, 640, 480 - 50 - 50);
        sr.line(640 - 50, 480 - 50, 640 + 50, 480 - 50);

        sr.end();
        Gdx.gl.glLineWidth(1);

        radarScreen.drawRadar2D(cam, sr, tank, obstacles, dt);
    }

    private void loadMapObstacles() {
        obstacles.clear();

        int[][] coords = new int[][]{
            {12, 96, 96, 0}, {15, 128, 64, 16}, {12, 128, 256, 32}, {15, 64, 256, 64},
            {12, 256, 256, 24}, {0, 256, 64, 40}, {1, 256, 128, 48}, {0, 64, 128, 56},
            {1, 80, 48, 64}, {15, 192, 104, 72}, {12, 137, 60, 80}, {0, 184, 64, 88},
            {1, 168, 244, 96}, {15, 236, 116, 104}, {12, 232, 152, 112}, {0, 152, 156, 120},
            {1, 16, 228, 128}, {15, 8, 180, 136}, {12, 64, 204, 144}, {0, 92, 196, 152},
            {1, 84, 140, 160}
        };

        for (int[] info : coords) {
            int type = info[0];
            int xb = info[1] & 0xFF;
            int zb = info[2] & 0xFF;
            int face = info[3] & 0xFF;

            int x16 = (xb << 8) & 0xFFFF;
            int z16 = (zb << 8) & 0xFFFF;

            float x = (float) x16;
            float z = (float) z16;
            float y = 0.5f;
            float deg = face * (360f / 256f);

            GameModelInstance inst = Models.buildWireframeInstance(Models.Mesh.values()[type].wf(), Color.GREEN, 1f, -1f, x, y, z);
            inst.transform.rotate(Vector3.Y, deg);

            obstacles.add(inst);
        }
    }

    private void drawObstacles(ModelBatch batch) {
        for (GameModelInstance inst : obstacles) {
            Vector3 wrapped = nearestWrappedPos(inst, cam.position.x, cam.position.z, TMP1);
            if (!cam.frustum.pointInFrustum(wrapped)) {
                continue;
            }

            MAT1.set(inst.transform).setTranslation(wrapped);
            inst.transform.set(MAT1);
            batch.render(inst, environment);
        }
    }

    private static float obstacleRadiusFromBounds(GameModelInstance inst) {
        float rx = inst.localBounds.getWidth();
        float rz = inst.localBounds.getDepth();
        return Math.max(rx, rz);
    }

    private boolean collidesAnyModelXZ(float x, float z) {
        for (GameModelInstance inst : obstacles) {
            Vector3 wrapped = nearestWrappedPos(inst, x, z, TMP1);
            float dx = wrapped.x - x;
            float dz = wrapped.z - z;
            float r = ENEMY_COLLISION_RADIUS + obstacleRadiusFromBounds(inst);
            if (dx * dx + dz * dz <= r * r) {
                return true;
            }
        }
        return false;
    }

    public static float wrap16f(float v) {
        float r = v % WORLD_WRAP_16BIT;
        if (r < 0) {
            r += WORLD_WRAP_16BIT;
        }
        return r;
    }

    public static float wrapDelta16(float d) {
        if (d > WORLD_WRAP_HALF_16BIT) {
            return d - WORLD_WRAP_16BIT;
        }
        if (d < -WORLD_WRAP_HALF_16BIT) {
            return d + WORLD_WRAP_16BIT;
        }
        return d;
    }

    public static float to16(float v) {
        return Math.round(v) & 0xFFFF;
    }

    public static Vector3 nearestWrappedPos(GameModelInstance inst, float refX, float refZ, Vector3 out) {
        float refX16 = to16(refX);
        float refZ16 = to16(refZ);

        float obX16 = Math.round(inst.initialPos.x);
        float obZ16 = Math.round(inst.initialPos.z);

        float dx16 = wrapDelta16(obX16 - refX16);
        float dz16 = wrapDelta16(obZ16 - refZ16);

        float wx = refX + dx16;
        float wz = refZ + dz16;

        return out.set(wx, inst.initialPos.y, wz);
    }

    private static int rand8() {
        return ThreadLocalRandom.current().nextInt(256);
    }

    private static void randomSpawn(Vector3 pos, GameContext ctx) {
        while (true) {
            int rx = ((rand8() & 0xFF) << 8) | (rand8() & 0xFF);
            int rz = ((rand8() & 0xFF) << 8) | (rand8() & 0xFF);

            float x = BattleZone.wrap16f((float) rx);
            float z = BattleZone.wrap16f((float) rz);

            if (!ctx.collisionChecker.collides(x, z)) {
                pos.x = x;
                pos.y = 0.5f;
                pos.z = z;
                return;
            }
        }
    }

}
