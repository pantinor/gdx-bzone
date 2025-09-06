package bzone;

import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;

public class GameModelInstance extends ModelInstance {

    public final BoundingBox localBounds = new BoundingBox();
    public final Vector3 initialPos = new Vector3();

    public GameModelInstance(Model model, float x, float y, float z) {
        super(model);
        this.initialPos.set(x, y, z);
        this.transform.setToTranslation(x, y, z);
        this.calculateBoundingBox(localBounds);
    }

    public float getX() {
        return this.transform.val[12];
    }

    public float getY() {
        return this.transform.val[13];
    }

    public float getZ() {
        return this.transform.val[14];
    }
}
