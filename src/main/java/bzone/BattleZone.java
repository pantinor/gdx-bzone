package bzone;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.controllers.Controller;
import com.badlogic.gdx.controllers.ControllerListener;
import com.badlogic.gdx.controllers.Controllers;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL30;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import java.util.ArrayList;
import java.util.List;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Timer;
import java.util.concurrent.ThreadLocalRandom;

public class BattleZone implements ApplicationListener, InputProcessor, ControllerListener {

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

    private static final int[][] LIFE_ICON_STROKES = {
        {0, 0, -6, 6, 3, 9, 6, 15, 42, 6, 36, 0, 0, 0},
        {18, 12, 39, 12, 39, 9, 30, 9}
    };

    private static final float YAW_SPEED_DEG = 90f;
    private static final float MOVE_SPEED = 3200f;

    private boolean wDown, aDown, sDown, dDown;
    private boolean rstickFwd, rstickBck, lstickFwd, lstickBck;
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

    private BaseTank tank;
    private Missile missile;
    private Projectile tankProjectile;
    private Projectile playerProjectile;
    private TankExplosion explosion;
    private final Spatter spatter = new Spatter(1, 0, 0);
    private Title title;

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
        cam.far = 72000f;
        cam.update();

        sr = new ShapeRenderer();

        Gdx.input.setInputProcessor(this);
        Controllers.addListener(this);

        environment = new Environment();
        this.environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.05f, 0.05f, 0.05f, 1f));

        modelBatch = new ModelBatch();

        GameModelInstance tm = Models.buildWireframeInstance(Models.Mesh.SLOW_TANK.wf(), Color.GREEN, 1, -1f, 3f, 0.5f, 3f);
        GameModelInstance rm = Models.buildWireframeInstance(Models.Mesh.RADAR1.wf(), Color.GREEN, 1, -1f, 3f, 0.5f, 3f);
        GameModelInstance mm = Models.buildWireframeInstance(Models.Mesh.MISSILE.wf(), Color.WHITE, 1, -1f, 3f, 0.5f, 3f);

        GameModelInstance logoba = Models.buildWireframeInstance(Models.Mesh.LOGO_BA.wf(), Color.GREEN, 1, -1f, 3f, 0.5f, 3f);
        GameModelInstance logottle = Models.buildWireframeInstance(Models.Mesh.LOGO_TTLE.wf(), Color.GREEN, 1, -1f, 3f, 0.5f, 3f);
        GameModelInstance logozone = Models.buildWireframeInstance(Models.Mesh.LOGO_ZONE.wf(), Color.GREEN, 1, -1f, 3f, 0.5f, 3f);

        this.tank = new Tank(tm, rm);
        this.missile = new Missile(mm);
        this.explosion = new TankExplosion(Color.GREEN);

        context.collisionChecker = this::hitsAnyObstacles;
        context.hitChecker = this::hitsEnemy;
        context.tankSpawn = this::tankSpawn;
        context.playerSpawn = this::playerSpawn;

        randomSpawn(cam.position, context);
        randomSpawn(this.tank.pos, context);

        headingDeg = 0;//0 is facing the moon

        this.title = new Title(logoba, logottle, logozone);
        this.title.setPosition(cam.position.x, cam.position.z);

        GameModelInstance tankProj = Models.buildWireframeInstance(Models.Mesh.PROJECTILE.wf(), Color.YELLOW, 1, -1f, 0f, 0.5f, 0f);
        tankProjectile = new Projectile(tankProj);
        context.shooter = () -> tankProjectile.spawnFromTank(this.tank, context);

        GameModelInstance playerProj = Models.buildWireframeInstance(Models.Mesh.PROJECTILE.wf(), Color.YELLOW, 1, -1f, 0f, 0.5f, 0f);
        playerProjectile = new Projectile(playerProj);

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
        if (aDown || rstickFwd || lstickBck) {
            yaw += YAW_SPEED_DEG * dt;
        }
        if (dDown || lstickFwd || rstickBck) {
            yaw -= YAW_SPEED_DEG * dt;
        }
        if (yaw != 0f) {
            cam.rotate(Vector3.Y, yaw);
            headingDeg = (headingDeg + yaw) % 360f;
        }

        float move = 0f;
        if (wDown || (rstickFwd && lstickFwd)) {
            move += MOVE_SPEED * dt;
        }
        if (sDown || (rstickBck && lstickBck)) {
            move -= MOVE_SPEED * dt;
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

        tank.update(context, dt);
        tankProjectile.update(context, obstacles, dt, false);
        playerProjectile.update(context, obstacles, dt, true);
        missile.update(context, dt);
        explosion.update(dt, context.tankSpawn);
        spatter.update(dt);
        //engine.update(dt);

        Gdx.gl.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        Gdx.gl.glClear(GL30.GL_COLOR_BUFFER_BIT | GL30.GL_DEPTH_BUFFER_BIT);

        modelBatch.begin(cam);

        drawObstacles(modelBatch);

        if (tankProjectile.active) {
            modelBatch.render(tankProjectile.inst, environment);
        }
        if (playerProjectile.active) {
            modelBatch.render(playerProjectile.inst, environment);
        }

        tank.render(modelBatch, environment);
        missile.render(modelBatch, environment);
        explosion.render(modelBatch, environment);

        if (title != null) {
            title.render(modelBatch, environment);
        }

        modelBatch.end();

        sr.setProjectionMatrix(cam.combined);
        spatter.render(sr);

        backGroundCam.setToOrtho(false, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        backGroundCam.update();

        sr.setProjectionMatrix(backGroundCam.combined);

        context.hdFromCam = (MathUtils.atan2(cam.direction.x, cam.direction.z) * MathUtils.radiansToDegrees + 360f) % 360f;
        float hd = (headingDeg % 360f + 360f) % 360f;

        background.drawBackground2D(sr, hd);

        Gdx.gl.glEnable(GL30.GL_BLEND);
        Gdx.gl.glBlendFunc(GL30.GL_SRC_ALPHA, GL30.GL_ONE_MINUS_SRC_ALPHA);

        drawHUD(dt);

        //batch.begin();
        //font.draw(batch, "hd " + hd + " pos = " + cam.position.x + " " + cam.position.z, 100, SCREEN_HEIGHT - 100);
        //batch.end();
    }

    @Override
    public boolean keyDown(int keycode) {
        title = null;

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
                missile.spawn(context);
                return true;
            case Input.Keys.SPACE:
                playerProjectile.spawnFromPlayer(context);
                return true;
            case Input.Keys.NUM_6:
                explosion.spawn(to16(tank.pos.x), to16(tank.pos.z));
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
    public void connected(Controller cntrlr) {
    }

    @Override
    public void disconnected(Controller cntrlr) {
    }

    @Override
    public boolean buttonDown(Controller c, int buttonCode) {
        playerProjectile.spawnFromPlayer(context);
        return false;
    }

    @Override
    public boolean buttonUp(Controller c, int buttonCode) {
        return false;
    }

    @Override
    public boolean axisMoved(Controller c, int axisCode, float value) {

        if (axisCode == 1) {
            if (value > 0.5f) {
                lstickBck = true;
            } else if (value < -0.5f) {
                lstickFwd = true;
            } else {
                lstickFwd = false;
                lstickBck = false;
            }
        }

        if (axisCode == 3) {
            if (value > 0.5f) {
                rstickBck = true;
            } else if (value < -0.5f) {
                rstickFwd = true;
            } else {
                rstickBck = false;
                rstickFwd = false;
            }
        }

        if (rstickFwd || rstickBck || lstickFwd || lstickBck) {
            engine.setThrottle(1f);
        } else {
            engine.setThrottle(0f);
        }

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

        radarScreen.drawRadar2D(cam, sr, tank, missile, obstacles, dt);

        if (context.lives > 0) {
            sr.begin(ShapeRenderer.ShapeType.Line);
            sr.setColor(Color.RED);
            float ox = 800;
            float oy = SCREEN_HEIGHT - 50;
            float scale = 1.3f;
            for (int life = 0; life < context.lives; life++) {
                for (int[] s : LIFE_ICON_STROKES) {
                    for (int i = 0; i + 3 < s.length; i += 2) {
                        float x1 = ox + s[i] * scale, y1 = oy + s[i + 1] * scale;
                        float x2 = ox + s[i + 2] * scale, y2 = oy + s[i + 3] * scale;
                        sr.line(x1, y1, x2, y2);
                    }
                }
                ox += 75;
            }
            sr.end();
        }

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

    private boolean hitsAnyObstacles(float x, float z) {
        for (GameModelInstance inst : obstacles) {
            boolean collides = touches(inst, x, z);
            if (collides) {
                spatter.spawn(to16(x), to16(z));
                return true;
            }
        }
        return false;
    }

    private boolean touches(GameModelInstance inst, float x, float z) {
        Vector3 wrapped = nearestWrappedPos(inst, x, z, TMP1);
        float dx = wrapped.x - x;
        float dz = wrapped.z - z;
        float r = obstacleRadiusFromBounds(inst);
        return dx * dx + dz * dz <= r * r;
    }

    private boolean hitsEnemy(float x, float z) {
        if (this.tank.alive && touches(this.tank.inst, x, z)) {
            this.tank.alive = false;
            explosion.spawn(to16(tank.pos.x), to16(tank.pos.z));
            spatter.spawn(to16(x), to16(z));
            randomSpawn(this.tank.pos, context);
            tank.applyWrappedTransform(context);
            return true;
        }
        if (this.missile.active && touches(this.missile.inst, x, z)) {
            this.missile.active = false;
            spatter.spawn(to16(x), to16(z));
            return true;
        }
        return false;
    }

    private void tankSpawn() {
        this.tank.alive = true;
        Sounds.play(Sounds.Effect.SPAWN);
    }

    private void playerSpawn() {
        context.spawnProtected = 0;
        context.lives--;
        Timer.schedule(new Timer.Task() {
            @Override
            public void run() {
                randomSpawn(cam.position, context);
                Sounds.play(Sounds.Effect.SPAWN);
            }
        }, 5);
    }

    public static Vector3 nearestWrappedPos(GameModelInstance inst, float x, float z, Vector3 out) {
        float refX16 = to16(x);
        float refZ16 = to16(z);

        float obX16 = Math.round(inst.getX());
        float obZ16 = Math.round(inst.getZ());

        float dx16 = wrapDelta16(obX16 - refX16);
        float dz16 = wrapDelta16(obZ16 - refZ16);

        float wx = x + dx16;
        float wz = z + dz16;

        return out.set(wx, inst.getY(), wz);
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
