package bzone;

import bzone.Models.Mesh;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;

public class GameModelInstance extends ModelInstance {

    public final Mesh mesh;
    public final BoundingBox localBounds = new BoundingBox();
    public final Vector3 initialPos = new Vector3();

    public GameModelInstance(Mesh mesh, Model model) {
        super(model);
        this.mesh = mesh;
        this.calculateBoundingBox(localBounds);
    }

    public Mesh mesh() {
        return mesh;
    }

    public float getX() {
        return this.transform.val[Matrix4.M03];
    }

    public float getY() {
        return this.transform.val[Matrix4.M13];
    }

    public float getZ() {
        return this.transform.val[Matrix4.M23];
    }
}
