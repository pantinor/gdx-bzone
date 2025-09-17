package bzone;

import static bzone.Models.LIFE_ICON_STROKES;
import bzone.Models.Mesh;
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
import com.badlogic.gdx.math.collision.BoundingBox;
import com.badlogic.gdx.utils.Timer;

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

    /**
     * Size of the toroidal world in the original 16-bit ROM coordinate space.
     */
    public static final int WORLD_WRAP_16BIT = 1 << 16; // 65536

    /**
     * Half of {@link #WORLD_WRAP_16BIT}. Used to bring deltas into the signed
     * range [-32768, +32767] so we can choose the nearest wrapped “image” of an
     * object when computing distances or offsets.
     */
    public static final int WORLD_WRAP_HALF_16BIT = WORLD_WRAP_16BIT >>> 1; // 32768

    public static final float WORLD_Y = 0;
    public static final float PLAYER_Y = 480;

    private static final float YAW_SPEED_DEG = 30f;
    private static final float MOVE_SPEED = 3200f;
    private static final int MAX_INACTIVITY = 900;

    private boolean wDown, aDown, sDown, dDown, blocked;
    private boolean rstickFwd, rstickBck, lstickFwd, lstickBck;
    private float headingDeg = 0f;

    private SpriteBatch batch;
    private ModelBatch modelBatch;
    private PerspectiveCamera cam;
    private OrthographicCamera backGroundCam;
    private Environment environment;
    private final List<GameModelInstance> obstacles = new ArrayList<>(21);
    private ShapeRenderer sr;
    private Background background;

    private final GameContext context = new GameContext();
    private int nmiCount = 0;

    private BaseTank tank;
    private Missile missile;
    private Saucer saucer;
    private BaseTank flyer;
    private Projectile tankProjectile, flyerProjectile, playerProjectile;
    private TankExplosion explosion;
    private final Spatter spatter = new Spatter();
    private Title title;

    private final Radar radarScreen = new Radar();
    private EngineSound engine;

    BitmapFont font;

    @Override
    public void create() {

        Sounds.MUTE = false;

        FreeTypeFontGenerator generator = new FreeTypeFontGenerator(Gdx.files.classpath("assets/data/bzone-font.ttf"));
        FreeTypeFontGenerator.FreeTypeFontParameter parameter = new FreeTypeFontGenerator.FreeTypeFontParameter();

        parameter.size = 36;
        parameter.color = Color.RED;
        parameter.hinting = FreeTypeFontGenerator.Hinting.Full;
        font = generator.generateFont(parameter);

        batch = new SpriteBatch();

        backGroundCam = new OrthographicCamera();
        backGroundCam.setToOrtho(false, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        backGroundCam.far = 16;
        backGroundCam.update();

        cam = new PerspectiveCamera(67, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        cam.near = 1f;
        cam.far = 72000f;
        cam.update();

        sr = new ShapeRenderer();

        Gdx.input.setInputProcessor(this);
        Controllers.addListener(this);

        environment = new Environment();
        environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.05f, 0.05f, 0.05f, 1f));

        modelBatch = new ModelBatch();

        background = new Background();

        GameModelInstance tankProj = Models.buildWireframeInstance(Models.Mesh.PROJECTILE, Color.RED, 1);
        tankProjectile = new Projectile(tankProj);

        GameModelInstance playerProj = Models.buildWireframeInstance(Models.Mesh.PROJECTILE, Color.YELLOW, 1);
        playerProjectile = new Projectile(playerProj);

        GameModelInstance flyerProj = Models.buildWireframeInstance(Models.Mesh.ROCKET, Color.BLUE, 1);
        flyerProjectile = new Projectile(flyerProj);

        GameModelInstance tm = Models.buildWireframeInstance(Models.Mesh.SLOW_TANK, Color.GREEN, 1);
        GameModelInstance stm = Models.buildWireframeInstance(Models.Mesh.SUPER_TANK, Color.GREEN, 1);
        GameModelInstance rm = Models.buildWireframeInstance(Models.Mesh.RADAR, Color.GREEN, 1);
        GameModelInstance mm = Models.buildWireframeInstance(Models.Mesh.MISSILE, Color.GREEN, 1);
        GameModelInstance sm = Models.buildWireframeInstance(Models.Mesh.SAUCER, Color.GREEN, 1);

        GameModelInstance logoba = Models.buildWireframeInstance(Models.Mesh.LOGO_BA, Color.GREEN, 1, 20, true);
        GameModelInstance logottle = Models.buildWireframeInstance(Models.Mesh.LOGO_TTLE, Color.GREEN, 1, 20, true);
        GameModelInstance logozone = Models.buildWireframeInstance(Models.Mesh.LOGO_ZONE, Color.GREEN, 1, 20, true);

        this.tank = new Tank(tm, stm, rm, tankProjectile);
        this.flyer = new Skimmer(flyerProjectile);
        this.missile = new Missile(mm);
        this.saucer = new Saucer(sm);

        this.explosion = new TankExplosion(Color.GREEN);

        context.collisionChecker = this::collidesObstacle;
        context.hitsEnemy = this::hitsEnemy;
        context.hitsObstacle = this::hitsObstacle;
        context.tankSpawn = this::tankSpawn;
        context.playerSpawn = this::playerSpawn;
        context.saucer_ttl = MathUtils.random(12, 15) * 100;

        randomSpawn(cam.position, context);
        randomSpawn(this.tank.pos, context);

        cam.position.y = PLAYER_Y;
        headingDeg = 0;//0 is facing the moon

        this.title = new Title(logoba, logottle, logozone);
        this.title.pos.set(cam.position.x, cam.position.y - 1000, cam.position.z);

        loadMapObstacles();

        engine = new EngineSound();
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
            float sx = cam.position.x, sz = cam.position.z;
            float nx = wrap16f(sx + TMP1.x);
            float nz = wrap16f(sz + TMP1.z);
            if (!blockedAt(nx, nz)) {
                blocked = false;
                cam.position.x = nx;
                cam.position.z = nz;
                Sounds.Effect.ERROR.sound().stop();
            } else {
                if (!blocked) {
                    Sounds.play(Sounds.Effect.BUMP);
                    Sounds.play(Sounds.Effect.ERROR);
                }
                blocked = true;
            }
        }

        cam.update(true);

        context.playerX = cam.position.x;
        context.playerZ = cam.position.z;
        context.nmiCount = ++this.nmiCount;
        context.saucer_ttl--;
        if (context.inactivityCount != MAX_INACTIVITY) {
            context.inactivityCount = Math.min(MAX_INACTIVITY, context.inactivityCount + 1);
        }

        context.hdFromCam = (MathUtils.atan2(cam.direction.x, cam.direction.z) * MathUtils.radiansToDegrees + 360f) % 360f;
        float hd = (headingDeg % 360f + 360f) % 360f;

        tank.update(context, dt);
        flyer.update(context, dt);
        tankProjectile.update(context, obstacles, dt, false);
        flyerProjectile.update(context, obstacles, dt, false);
        playerProjectile.update(context, obstacles, dt, true);
        missile.update(context, dt);
        saucer.update(context, dt);
        explosion.update(dt, context.tankSpawn);
        spatter.update(dt);
        engine.update(dt);

        Gdx.gl.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        Gdx.gl.glClear(GL30.GL_COLOR_BUFFER_BIT | GL30.GL_DEPTH_BUFFER_BIT);

        modelBatch.begin(cam);

        drawObstacles(modelBatch);

        if (tankProjectile.active) {
            nearestWrappedPos(tankProjectile.inst, cam.position.x, cam.position.z, TMP1);
            if (cam.frustum.pointInFrustum(TMP1)) {
                tankProjectile.inst.transform.val[Matrix4.M03] = TMP1.x;
                tankProjectile.inst.transform.val[Matrix4.M13] = TMP1.y;
                tankProjectile.inst.transform.val[Matrix4.M23] = TMP1.z;
                modelBatch.render(tankProjectile.inst, environment);
            }
        }
        if (flyerProjectile.active) {
            nearestWrappedPos(flyerProjectile.inst, cam.position.x, cam.position.z, TMP1);
            if (cam.frustum.pointInFrustum(TMP1)) {
                flyerProjectile.inst.transform.val[Matrix4.M03] = TMP1.x;
                flyerProjectile.inst.transform.val[Matrix4.M13] = TMP1.y;
                flyerProjectile.inst.transform.val[Matrix4.M23] = TMP1.z;
                modelBatch.render(flyerProjectile.inst, environment);
            }
        }
        if (playerProjectile.active) {
            nearestWrappedPos(playerProjectile.inst, cam.position.x, cam.position.z, TMP1);
            if (cam.frustum.pointInFrustum(TMP1)) {
                playerProjectile.inst.transform.val[Matrix4.M03] = TMP1.x;
                playerProjectile.inst.transform.val[Matrix4.M13] = TMP1.y;
                playerProjectile.inst.transform.val[Matrix4.M23] = TMP1.z;
                modelBatch.render(playerProjectile.inst, environment);
            }
        }

        tank.render(cam, context, modelBatch, environment);
        flyer.render(cam, context, modelBatch, environment);
        missile.render(cam, modelBatch, environment);
        saucer.render(cam, modelBatch, environment);
        explosion.render(cam, modelBatch, environment);

        if (title != null) {
            title.render(modelBatch, environment);
        }

        modelBatch.end();
        //end 3D render

        //draw 2D spatter
        sr.setProjectionMatrix(cam.combined);
        spatter.render(sr);

        //start 2D render
        backGroundCam.update();
        sr.setProjectionMatrix(backGroundCam.combined);
        modelBatch.begin(backGroundCam);
        background.drawBackground2D(sr, modelBatch, environment, hd);
        modelBatch.end();

        Gdx.gl.glEnable(GL30.GL_BLEND);
        Gdx.gl.glBlendFunc(GL30.GL_SRC_ALPHA, GL30.GL_ONE_MINUS_SRC_ALPHA);

        drawHUD(dt);

        batch.begin();
        font.draw(batch, "SCORE  " + context.playerScore, 800, SCREEN_HEIGHT - 80);
        batch.end();

        if (context.inactivityCount == MAX_INACTIVITY) {
            randomSpawnDistantInView(context, this.missile.pos, 6000f);
            missile.spawn(context);
            context.inactivityCount = 0;
        }
        if (context.saucer_ttl == 0) {
            context.saucer_ttl = MathUtils.random(12, 15) * 100;
            randomSpawnDistantInView(context, this.saucer.pos, WORLD_Y);
            saucer.spawn();
        }
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
                //context.playerScore = 1;
                return true;
            case Input.Keys.NUM_2:
                //context.playerScore = -1;
                return true;
            case Input.Keys.NUM_3:
                //context.playerScore = 0;
                return true;
            case Input.Keys.NUM_4:
                //randomSpawnDistantInView(context, this.missile.pos, 6000f);
                //missile.spawn(context);
                return true;
            case Input.Keys.SPACE:
                if (context.alive) {
                    playerProjectile.spawnFromPlayer(context);
                }
                return true;
            case Input.Keys.NUM_6:
                //randomSpawnDistantInView(context, this.saucer.pos, WORLD_Y);
                //saucer.spawn();
                return true;
            case Input.Keys.NUM_7:
                //explosion.spawn(true, to16(cam.position.x + 6000), to16(cam.position.z + 6000));
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
        float reticleIntensity = 0.6f;

        Gdx.gl.glLineWidth(2);
        sr.begin(ShapeRenderer.ShapeType.Line);
        sr.setColor(0f, reticleIntensity, 0f, 1f);

        //reticle
        final float cx = 640, cy = 480;
        final float X = 75f;    // half-width
        final float Y = 75f;    // bar offset from center
        final float T = 25f;    // tick length
        final float L = 100f;   // long center segment length

        sr.line(cx, cy - (Y + L), cx, cy - Y);
        sr.line(cx - X, cy - Y, cx + X, cy - Y);
        sr.line(cx - X, cy - Y, cx - X, cy - (Y - T));
        sr.line(cx + X, cy - Y, cx + X, cy - (Y - T));
        sr.line(cx, cy + Y, cx, cy + (Y + L));
        sr.line(cx - X, cy + Y, cx + X, cy + Y);
        sr.line(cx - X, cy + (Y - T), cx - X, cy + Y);
        sr.line(cx + X, cy + (Y - T), cx + X, cy + Y);

        sr.end();
        Gdx.gl.glLineWidth(1);

        radarScreen.drawRadar2D(cam, sr, tank, missile, saucer, flyer, obstacles, dt);

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

        if (!context.alive) {
            Models.drawDeathCracks(sr);
        }

    }

    private void loadMapObstacles() {
        obstacles.clear();

        int[][] coords = new int[][]{
            {2, 96, 96, 0},
            {3, 128, 64, 16},
            {2, 128, 256, 32},
            {3, 64, 256, 64},
            {2, 256, 256, 24},
            {0, 256, 64, 40},
            {1, 256, 128, 48},
            {0, 64, 128, 56},
            {1, 80, 48, 64},
            {3, 192, 104, 72},
            {2, 137, 60, 80},
            {0, 184, 64, 88},
            {1, 168, 244, 96},
            {3, 236, 116, 104},
            {2, 232, 152, 112},
            {0, 152, 156, 120},
            {1, 16, 228, 128},
            {3, 8, 180, 136},
            {2, 64, 204, 144},
            {0, 92, 196, 152},
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
            float y = WORLD_Y;
            float deg = face * (360f / 256f);

            GameModelInstance inst = Models.buildWireframeInstance(Models.Mesh.values()[type], Color.GREEN, 1f);
            inst.initialPos.set(x, y, z);
            inst.transform.setToTranslation(x, y, z);
            inst.transform.rotate(Vector3.Y, deg);

            obstacles.add(inst);
        }
    }

    private void drawObstacles(ModelBatch batch) {
        for (int i = 0, n = obstacles.size(); i < n; i++) {
            GameModelInstance inst = obstacles.get(i);
            nearestWrappedPos(inst, cam.position.x, cam.position.z, TMP1);

            if (!cam.frustum.pointInFrustum(TMP1)) {
                continue;
            }

            inst.transform.val[Matrix4.M03] = TMP1.x; // x
            inst.transform.val[Matrix4.M13] = TMP1.y; // y
            inst.transform.val[Matrix4.M23] = TMP1.z; // z

            batch.render(inst, environment);
        }
    }

    private boolean blockedAt(float x, float z) {
        if (collidesObstacle(x, z)) {
            return true;
        }
        if (this.tank.alive && touches(this.tank.inst, x, z)) {
            return true;
        }
        if (this.missile.active && touches(this.missile.inst, x, z)) {
            return true;
        }
        if (this.saucer.active && touches(this.saucer.inst, x, z)) {
            return true;
        }
        return false;
    }

    private boolean collidesObstacle(float x, float z) {
        for (GameModelInstance inst : obstacles) {
            boolean collides = touches(inst, x, z);
            if (collides) {
                return true;
            }
        }
        return false;
    }

    private boolean hitsObstacle(float x, float z) {
        for (GameModelInstance inst : obstacles) {
            if (inst.mesh().equals(Mesh.SHORT_BOX)) {
                continue;//shoots over the short boxes
            }
            boolean hits = touches(inst, x, z);
            if (hits) {
                spatter.spawn(to16(x), to16(z));
                return true;
            }
        }
        return false;
    }

    private boolean hitsEnemy(float x, float z) {
        if (this.tank.alive && touches(this.tank.inst, x, z)) {
            this.tank.alive = false;
            context.playerScore += 1000;
            explosion.spawn(true, to16(tank.pos.x), to16(tank.pos.z));
            spatter.spawn(to16(x), to16(z));
            randomSpawn(this.tank.pos, context);
            tank.applyWrappedTransform(context);
            return true;
        }
        if (this.flyer.alive && touches(this.flyer.inst, x, z) && this.flyer.pos.y < 800) {
            this.flyer.alive = false;
            context.playerScore += 1000;
            spatter.spawn(to16(x), to16(z));
            randomSpawnDistantInView(context, this.flyer.pos, 0);
            flyer.applyWrappedTransform(context);
            return true;
        }
        if (this.missile.active && touches(this.missile.inst, x, z)) {
            this.missile.active = false;
            context.playerScore += 2000;
            explosion.spawn(false, to16(missile.pos.x), to16(missile.pos.z));
            spatter.spawn(to16(x), to16(z));
            return true;
        }
        if (this.saucer.active && touches(this.saucer.inst, x, z)) {
            this.saucer.kill();
            Sounds.play(Sounds.Effect.SAUCER_HIT);
            context.playerScore += 5000;
            spatter.spawn(to16(x), to16(z));
            return true;
        }
        return false;
    }

    private void tankSpawn() {
        this.tank.alive = true;
        Sounds.play(Sounds.Effect.SPAWN);

        if (context.playerScore > 10000) {
            if (MathUtils.random(1, 3) == 1) {
                randomSpawnDistantInView(context, this.missile.pos, 6000f);
                missile.spawn(context);
            }
        }

        if (MathUtils.random(1, 3) == 1 && !this.flyer.alive) {
            randomSpawnDistantInView(context, this.flyer.pos, 0);
            this.flyer.alive = true;
        }
    }

    private void playerSpawn() {
        context.spawnProtected = 0;
        context.lives--;
        context.enemyScore++;
        context.alive = false;
        Timer.schedule(new Timer.Task() {
            @Override
            public void run() {
                randomSpawn(cam.position, context);
                cam.position.y = PLAYER_Y;
                Sounds.play(Sounds.Effect.SPAWN);
                context.alive = true;
            }
        }, 5);
    }

    private boolean touches(GameModelInstance inst, float x, float z) {
        nearestWrappedPos(inst, x, z, TMP1);
        final float dx = x - TMP1.x;
        final float dz = z - TMP1.z;

        final float[] m = inst.transform.val;
        float lx = m[Matrix4.M00] * dx + m[Matrix4.M20] * dz;
        float lz = m[Matrix4.M02] * dx + m[Matrix4.M22] * dz;

        final BoundingBox b = inst.localBounds;
        final float cx = (b.min.x + b.max.x) * 0.5f;
        final float cz = (b.min.z + b.max.z) * 0.5f;
        final float hx = (b.max.x - b.min.x) * 0.5f;
        final float hz = (b.max.z - b.min.z) * 0.5f;

        lx -= cx;
        lz -= cz;

        return (lx >= -hx && lx <= hx && lz >= -hz && lz <= hz);
    }

    public static void nearestWrappedPos(GameModelInstance inst, float x, float z, Vector3 out) {
        float refX16 = to16(x);
        float refZ16 = to16(z);

        float obX16 = inst.getX();
        float obZ16 = inst.getZ();

        float dx16 = wrapDelta16(obX16 - refX16);
        float dz16 = wrapDelta16(obZ16 - refZ16);

        float wx = x + dx16;
        float wz = z + dz16;

        out.set(wx, inst.getY(), wz);
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

    private static void randomSpawn(Vector3 pos, GameContext ctx) {
        
        for (int i = 0; i < 15; i++) {
            float r = MathUtils.random(16000, 31000);
            float x = wrap16f(ctx.playerX + 1 * r);
            float z = wrap16f(ctx.playerZ + 1 * r);

            if (!ctx.collisionChecker.collides(x, z)) {
                pos.x = x;
                pos.y = WORLD_Y;
                pos.z = z;
                return;
            }
        }
        
        Sounds.play(Sounds.Effect.OVERTURE);
        pos.set(wrap16f(ctx.playerX + 31000), WORLD_Y, wrap16f(ctx.playerZ));
    }

    private static void randomSpawnDistantInView(GameContext ctx, Vector3 pos, float y) {
        float HALF_ANGLE_DEG = 30f;
        float angleDeg = ctx.hdFromCam - HALF_ANGLE_DEG + MathUtils.random(0f, 2f * HALF_ANGLE_DEG);
        float angleRad = angleDeg * MathUtils.degreesToRadians;
        float r = MathUtils.random(29000, 31000);
        float x = wrap16f(ctx.playerX + MathUtils.sin(angleRad) * r);
        float z = wrap16f(ctx.playerZ + MathUtils.cos(angleRad) * r);
        pos.x = x;
        pos.y = y;
        pos.z = z;
    }

}
