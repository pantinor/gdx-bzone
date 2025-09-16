
import bzone.GameModelInstance;
import bzone.Models;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.utils.CameraInputController;
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.BaseDrawable;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import java.util.Map;

public class ModelViewer3D extends ApplicationAdapter {

    private PerspectiveCamera cam;
    private CameraInputController camController;
    private Environment environment;
    private ModelBatch modelBatch;

    // Scene content
    private ModelInstance ba;
    private ModelInstance ttle;
    private ModelInstance zone;
    private Map<String, ModelInstance> glyphs;

    private Model gridModel;
    private ModelInstance gridInstance;

    // UI
    private Stage stage;
    private Skin skin;
    private Slider xSlider, ySlider, zSlider;
    private Label xLabel, yLabel, zLabel;
    private SpriteBatch hudBatch;

    // Rotation state (degrees)
    private float rotX = 0f, rotY = 0f, rotZ = 0f;

    @Override
    public void create() {
        Gdx.gl.glClearColor(0.05f, 0.06f, 0.08f, 1f);
        modelBatch = new ModelBatch();
        hudBatch = new SpriteBatch();

        cam = new PerspectiveCamera(67, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        cam.position.set(0f, 500, 500);
        cam.lookAt(0f, 0f, 0f);
        cam.near = 1f;
        cam.far = 10000f;
        cam.update();

        camController = new CameraInputController(cam);
        camController.scrollFactor = -1.5f; // scroll to zoom speed

        environment = new Environment();
        environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.8f, 0.8f, 0.8f, 1f));
        environment.add(new DirectionalLight().set(1f, 1f, 1f, -1f, -0.8f, -0.2f));

        gridModel = buildXZGrid(64 /*lines*/, 1000 /*spacing*/, new Color(0.35f, 0.34f, 0.35f, 1f));
        gridInstance = new ModelInstance(gridModel, new Matrix4().idt());
        
        glyphs = Models.loadGlyphs(Color.RED, 1);


        //ba = Models.buildWireframeInstance(Models.Mesh.PROJECTILE, Color.GREEN, 1);
        //ttle = Models.buildWireframeInstance(Models.Mesh.ROCKET, Color.GREEN, 1);
        //zone = Models.buildWireframeInstance(Models.Mesh.GIR_DRAXON, Color.GREEN, 1);
        ba = glyphs.get("glyph_X");
        ttle = glyphs.get("glyph_Y");
        zone = glyphs.get("glyph_Z");

        stage = new Stage(new ScreenViewport());
        skin = makeMinimalSkin();
        makeKnobsUI();

        InputMultiplexer mux = new InputMultiplexer(stage, camController);
        Gdx.input.setInputProcessor(mux);
    }

    @Override
    public void render() {
        camController.update();

        rotX = xSlider.getValue();
        rotY = ySlider.getValue();
        rotZ = zSlider.getValue();

        xLabel.setText(String.format("Rotate X: %3.0f°", rotX));
        yLabel.setText(String.format("Rotate Y: %3.0f°", rotY));
        zLabel.setText(String.format("Rotate Z: %3.0f°", rotZ));

        ba.transform.idt()
                .rotate(Vector3.X, rotX)
                .rotate(Vector3.Y, rotY)
                .rotate(Vector3.Z, rotZ);

        ttle.transform.idt()
                .rotate(Vector3.X, rotX)
                .rotate(Vector3.Y, rotY)
                .rotate(Vector3.Z, rotZ);

        zone.transform.idt()
                .rotate(Vector3.X, rotX)
                .rotate(Vector3.Y, rotY)
                .rotate(Vector3.Z, rotZ);

        Gdx.gl.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

        modelBatch.begin(cam);
        modelBatch.render(gridInstance, environment);
        modelBatch.render(ba, environment);
        modelBatch.render(ttle, environment);
        //modelBatch.render(zone, environment);
        modelBatch.end();

        stage.act(Gdx.graphics.getDeltaTime());
        stage.draw();
    }

    @Override
    public void resize(int width, int height) {
        cam.viewportWidth = width;
        cam.viewportHeight = height;
        cam.update();
        stage.getViewport().update(width, height, true);
    }

    @Override
    public void dispose() {

    }

    private Model buildXZGrid(int halfLines, float spacing, Color color) {
        ModelBuilder mb = new ModelBuilder();
        mb.begin();
        Material mat = new Material(ColorAttribute.createDiffuse(color));
        MeshPartBuilder b = mb.part("xzGrid", GL20.GL_LINES,
                VertexAttributes.Usage.Position | VertexAttributes.Usage.ColorUnpacked, mat);
        b.setColor(color);

        float extent = halfLines * spacing;

        // Lines parallel to X (vary Z)
        for (int i = -halfLines; i <= halfLines; i++) {
            float z = i * spacing;
            b.line(-extent, 0f, z, +extent, 0f, z);
        }
        // Lines parallel to Z (vary X)
        for (int i = -halfLines; i <= halfLines; i++) {
            float x = i * spacing;
            b.line(x, 0f, -extent, x, 0f, +extent);
        }

        // Bold axes (X: red-ish, Z: blue-ish)
        Color xAxis = new Color(0.9f, 0.2f, 0.2f, 1f);
        Color zAxis = new Color(0.2f, 0.4f, 0.9f, 1f);

        b.setColor(xAxis);
        b.line(-extent, 0f, 0f, +extent, 0f, 0f);

        b.setColor(zAxis);
        b.line(0f, 0f, -extent, 0f, 0f, +extent);

        return mb.end();
    }

    private Skin makeMinimalSkin() {
        Skin s = new Skin();

        BitmapFont font = new BitmapFont(); // default font
        s.add("default-font", font, BitmapFont.class);

        // Drawables for slider background/knob
        Drawable bg = solid(6, 6, new Color(0.25f, 0.28f, 0.32f, 1f));
        Drawable knob = circle(16, new Color(0.85f, 0.85f, 0.90f, 1f));
        Drawable knobOver = circle(16, new Color(1f, 1f, 1f, 1f));

        Slider.SliderStyle sliderStyle = new Slider.SliderStyle();
        sliderStyle.background = bg;
        sliderStyle.knob = knob;
        sliderStyle.knobOver = knobOver;
        sliderStyle.knobDown = knobOver;

        Label.LabelStyle labelStyle = new Label.LabelStyle();
        labelStyle.font = font;
        labelStyle.fontColor = new Color(0.92f, 0.93f, 0.95f, 1f);

        s.add("default-horizontal", sliderStyle);
        s.add("default", labelStyle);

        return s;
    }

    private void makeKnobsUI() {
        stage.getRoot().setColor(1f, 1f, 1f, 1f);

        xSlider = new Slider(0f, 360f, 1f, false, skin);
        ySlider = new Slider(0f, 360f, 1f, false, skin);
        zSlider = new Slider(0f, 360f, 1f, false, skin);

        xLabel = new Label("Rotate X: 0°", skin);
        yLabel = new Label("Rotate Y: 0°", skin);
        zLabel = new Label("Rotate Z: 0°", skin);

        Table root = new Table();
        root.setFillParent(true);
        root.pad(10);
        root.bottom().left();

        // Rows: label + slider (wide)
        root.add(xLabel).left().padBottom(6f).row();
        root.add(xSlider).width(360).padBottom(10f).row();
        root.add(yLabel).left().padBottom(6f).row();
        root.add(ySlider).width(360).padBottom(10f).row();
        root.add(zLabel).left().padBottom(6f).row();
        root.add(zSlider).width(360).padBottom(10f).row();

        stage.addActor(root);
    }

    /* ===== Tiny drawables (programmatic) ===== */
    private Drawable solid(int w, int h, Color c) {
        Pixmap pm = new Pixmap(w, h, Pixmap.Format.RGBA8888);
        pm.setColor(c);
        pm.fill();
        Texture t = new Texture(pm);
        pm.dispose();
        return new TextureRegionDrawableFixed(t, w, h);
    }

    private Drawable circle(int diameter, Color c) {
        int r = diameter / 2;
        Pixmap pm = new Pixmap(diameter, diameter, Pixmap.Format.RGBA8888);
        pm.setBlending(Pixmap.Blending.None);
        pm.setColor(0, 0, 0, 0);
        pm.fill();

        pm.setBlending(Pixmap.Blending.SourceOver);
        pm.setColor(c);
        for (int y = -r; y <= r; y++) {
            int span = (int) Math.sqrt(r * r - y * y);
            pm.drawLine(r - span, r + y, r + span, r + y);
        }
        Texture t = new Texture(pm);
        pm.dispose();
        return new TextureRegionDrawableFixed(t, diameter, diameter);
    }

    /**
     * Minimal drawable that respects preferred size.
     */
    private static class TextureRegionDrawableFixed extends BaseDrawable implements Drawable {

        private final Texture texture;

        TextureRegionDrawableFixed(Texture texture, float prefW, float prefH) {
            this.texture = texture;
            setMinWidth(prefW);
            setMinHeight(prefH);
        }

        @Override
        public void draw(Batch batch, float x, float y, float width, float height) {
            batch.draw(texture, x, y, width, height);
        }
    }

    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration cfg = new Lwjgl3ApplicationConfiguration();
        cfg.setTitle("Model Viewer 3D");
        cfg.setWindowedMode(1280, 800);
        cfg.useVsync(true);
        cfg.setForegroundFPS(144);
        new Lwjgl3Application(new ModelViewer3D(), cfg);
    }

}
