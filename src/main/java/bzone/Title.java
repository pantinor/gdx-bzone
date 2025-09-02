package bzone;

import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.math.Vector3;

public class Title {

    private static final float DY_PER_FRAME = 8;
    private static final float DZ_PER_FRAME = 64;

    private final Vector3 pos = new Vector3(0, -1024, 0);

    private final GameModelInstance ba;
    private final GameModelInstance ttle;
    private final GameModelInstance zone;

    public Title(GameModelInstance ba, GameModelInstance ttle, GameModelInstance zone) {
        this.ba = ba;
        this.ttle = ttle;
        this.zone = zone;
    }
    
    public void setPosition(float x, float z) {
        pos.x = x;
        pos.z = z;
    }

    public void render(ModelBatch modelBatch, Environment environment) {

        pos.y += DY_PER_FRAME;
        pos.z -= DZ_PER_FRAME;

        ba.transform.idt().translate(pos.x, pos.y, pos.z).rotate(Vector3.X, -150);
        ttle.transform.idt().translate(pos.x, pos.y, pos.z).rotate(Vector3.X, -150);
        zone.transform.idt().translate(pos.x, pos.y, pos.z).rotate(Vector3.X, -150);

        modelBatch.render(this.ba, environment);
        modelBatch.render(this.ttle, environment);
        modelBatch.render(this.zone, environment);
    }

}
