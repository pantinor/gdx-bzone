package bzone;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import java.util.ArrayList;
import java.util.List;

public class TankExplosion {

    private static final int CHUNKS = 6;
    private static final float GRAVITY_PER_SEC = -12f;
    private static final float TERMINAL_FALL = -30f;
    private static final float SPIN_DEG_PER_SEC = 360f;
    private static final float LIFE_AFTER_GROUND = 0.10f;

    public static class Piece {

        final GameModelInstance inst;
        final Vector3 pos = new Vector3();
        final Vector3 vel = new Vector3();
        final Vector3 axis = new Vector3();
        float spinDeg;
        boolean grounded;
        float groundedTime;

        Piece(GameModelInstance inst) {
            this.inst = inst;
        }

        void reset() {
            pos.set(0, 0, 0);
            vel.set(0, 0, 0);
            axis.set(1, 0, 0);
            spinDeg = 0f;
            grounded = false;
            groundedTime = 0f;
            inst.transform.idt();
        }
    }

    private final List<Piece> pieces = new ArrayList<>(CHUNKS);
    private boolean finished = true;

    public TankExplosion(Color color) {
        Models.Mesh[] opts = new Models.Mesh[]{
            Models.Mesh.CHUNK0_TANK_10,
            Models.Mesh.CHUNK1_TANK_11,
            Models.Mesh.CHUNK2_TANK,
            Models.Mesh.CHUNK1_TANK_14,
            Models.Mesh.CHUNK0_TANK_15
        };

        for (int i = 0; i < CHUNKS; i++) {
            Models.Mesh mesh = opts[i % opts.length];
            GameModelInstance inst = Models.buildWireframeInstance(mesh.wf(), color, 1f, -1f, 0f, 0f, 0f);
            pieces.add(new Piece(inst));
        }
    }

    public boolean isFinished() {
        return finished;
    }

    public void spawn(Tank tank, boolean isSuperTank) {

        float yaw = tank.facing * MathUtils.PI2 / Tank.ANGLE_STEPS;

        TMP_F.set(MathUtils.sin(yaw), 0f, MathUtils.cos(yaw));
        TMP_R.set(MathUtils.cos(yaw), 0f, -MathUtils.sin(yaw));
        TMP_U.set(0f, 1f, 0f);

        finished = false;
        for (int i = 0; i < pieces.size(); i++) {
            Piece p = pieces.get(i);
            p.reset();

            float lift = (i % 2 == 0) ? 0.08f : 0.12f;
            p.pos.set(tank.pos).mulAdd(TMP_U, lift);

            float az = (i / (float) CHUNKS) * MathUtils.PI2;

            TMP_DIR.set(TMP_F).scl(MathUtils.cos(az)).mulAdd(TMP_R, MathUtils.sin(az)).nor();

            float radial = 10f + 5f * ((i & 1) == 0 ? 1 : -1);
            p.vel.set(TMP_DIR).scl(radial);
            p.vel.y = 18f + (i * 2f);

            p.axis.set(TMP_DIR).crs(TMP_U).nor();
            if (p.axis.isZero()) {
                p.axis.set(1, 0, 0);
            }
            p.spinDeg = (isSuperTank ? 90f : 45f) * ((i < 3) ? 1f : -1f);

            p.inst.transform.idt().translate(p.pos).rotate(p.axis, p.spinDeg);
        }
    }

    public void update(float dt) {
        if (finished) {
            return;
        }

        boolean allDone = true;
        for (int i = 0; i < pieces.size(); i++) {
            Piece c = pieces.get(i);

            if (!c.grounded) {
                c.vel.y += GRAVITY_PER_SEC * dt;
                if (c.vel.y < TERMINAL_FALL) {
                    c.vel.y = TERMINAL_FALL;
                }
                c.pos.mulAdd(c.vel, dt);

                if (c.pos.y <= 0f) {
                    c.pos.y = 0f;
                    c.grounded = true;
                    c.pos.y = 0f;
                } else {
                    allDone = false;
                }

                float spinStep = SPIN_DEG_PER_SEC * dt * (i < 3 ? 1f : -1f);
                c.spinDeg = (c.spinDeg + spinStep) % 360f;
            } else {
                c.groundedTime += dt;
                if (c.groundedTime < LIFE_AFTER_GROUND) {
                    allDone = false;
                }
            }

            c.inst.transform.idt()
                    .translate(c.pos)
                    .rotate(c.axis, c.spinDeg);
        }
        finished = allDone;
    }

    public void render(ModelBatch batch, Environment env) {

        if (finished) {
            return;
        }

        for (Piece p : pieces) {
            batch.render(p.inst, env);
        }
    }
    private static final Vector3 TMP_F = new Vector3();
    private static final Vector3 TMP_R = new Vector3();
    private static final Vector3 TMP_U = new Vector3();
    private static final Vector3 TMP_DIR = new Vector3();
}
