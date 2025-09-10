
import bzone.Models;
import bzone.TankExplosion;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.utils.CameraInputController;

public class TankExplosionTest extends ApplicationAdapter implements InputProcessor {

    private PerspectiveCamera cam;
    private CameraInputController camController;
    private Environment environment;
    private ModelBatch modelBatch;
    private TankExplosion explosion;

    private Model gridModel;
    private ModelInstance gridInstance;

    @Override
    public void create() {
        Gdx.gl.glClearColor(0.05f, 0.06f, 0.08f, 1f);
        modelBatch = new ModelBatch();

        cam = new PerspectiveCamera(67, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        cam.position.set(0f, 0, 30000);
        cam.lookAt(0f, 0f, 0f);
        cam.near = 1f;
        cam.far = 65000f;
        cam.update();

        camController = new CameraInputController(cam);
        camController.scrollFactor = -1.5f;

        environment = new Environment();
        environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.8f, 0.8f, 0.8f, 1f));
        environment.add(new DirectionalLight().set(1f, 1f, 1f, -1f, -0.8f, -0.2f));

        gridInstance = Models.buildXZGrid(65, 1000, new Color(0.35f, 0.34f, 0.35f, 1f));

        explosion = new TankExplosion(Color.ORANGE);

        InputMultiplexer mux = new InputMultiplexer(this, camController);
        Gdx.input.setInputProcessor(mux);
    }

    @Override
    public void render() {

        float dt = Gdx.graphics.getDeltaTime();

        camController.update();

        explosion.update(dt, null);

        Gdx.gl.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

        modelBatch.begin(cam);
        modelBatch.render(gridInstance, environment);
        explosion.render(cam, modelBatch, environment);
        modelBatch.end();

    }

    @Override
    public void resize(int width, int height) {
        cam.viewportWidth = width;
        cam.viewportHeight = height;
        cam.update();
    }

    @Override
    public void dispose() {

    }

    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration cfg = new Lwjgl3ApplicationConfiguration();
        cfg.setTitle("TankExplosionTest");
        cfg.setWindowedMode(1280, 800);
        cfg.useVsync(true);
        cfg.setForegroundFPS(144);
        new Lwjgl3Application(new TankExplosionTest(), cfg);
    }

    @Override
    public boolean keyDown(int keycode) {

        switch (keycode) {

            case Input.Keys.SPACE:
                explosion.spawn(true, 0, 0);
                return true;

            default:
                return false;
        }
    }

    @Override
    public boolean keyUp(int keycode) {
        return false;
    }

    @Override
    public boolean scrolled(float amountX, float amountY) {
        return false;
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
    public boolean touchCancelled(int screenX, int screenY, int pointer, int button) {
        return false;
    }

}
