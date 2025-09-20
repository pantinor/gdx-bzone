package bzone;

import static bzone.BattleZone.nearestWrappedPos;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.math.Vector3;
import java.util.ArrayList;
import java.util.List;
import bzone.GameContext.TankSpawn;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.collision.BoundingBox;

public class TankExplosion {

    private static final Vector3 TMP1 = new Vector3();

    private static final int CHUNKS = 6;

    private static final float GRAVITY = -4000f;
    private static final float INITIAL_SPEED_MIN = 5500f;
    private static final float INITIAL_SPEED_MAX = 6500f;
    private static final float DRAG = 0.95f;   // very light air drag (per-second, raised to dt)
    private static final float FRICTION = 0.60f;   // horizontal energy kept on bounce
    private static final float RESTITUTION = 0.25f;   // less pogo-sticking
    private static final float SPIN_DAMP = 0.85f;   // damp spin per bounce
    private static final float TTL_AFTER_SETTLE = 0.15f;   // disappear quickly once settled
    private static final float GROUND_Y = 0f;

    private static final float GROUND_EPS = 2f;   // how close to ground counts as contact
    private static final float STOP_BOUNCE_VY = 60f;  // vertical speed below this stops bouncing
    private static final float HVEL_EPS = 30f;  // horizontal speed threshold to sleep
    private static final float SLEEP_VEL_EPS = 12f;  // overall tiny speed to force sleep

    public static class Piece {

        final Vector3 pos = new Vector3();
        final Vector3 vel = new Vector3();
        final Vector3 axis = new Vector3(1, 0, 0);
        float spinDeg = 0f; // degrees per second around axis
        boolean grounded = false;
        float groundedTime = 0f;
        float size = 0.2f;
        final GameModelInstance inst;

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
            size = 0.2f;
            inst.transform.idt();
        }
    }

    private final List<Piece> tankPieces = new ArrayList<>(CHUNKS);
    private final List<Piece> missilePieces = new ArrayList<>(CHUNKS);

    private List<Piece> pieces = tankPieces;

    private final Vector3 origin = new Vector3();
    private boolean finished = false;

    public TankExplosion(Color color) {

        Models.Mesh[] meshes = new Models.Mesh[]{
            Models.Mesh.CHUNK3,
            Models.Mesh.CHUNK0,
            Models.Mesh.CHUNK1,
            Models.Mesh.CHUNK2,
            Models.Mesh.CHUNK_TANK
        };

        for (int i = 0; i < CHUNKS; i++) {
            Models.Mesh mesh = meshes[i % meshes.length];
            GameModelInstance inst = Models.getModelInstance(mesh, color, 1f);
            Piece p = new Piece(inst);
            p.size = computeHeight(inst);
            tankPieces.add(p);
        }

        meshes = new Models.Mesh[]{
            Models.Mesh.CHUNK3,
            Models.Mesh.CHUNK0,
            Models.Mesh.CHUNK1,
            Models.Mesh.CHUNK2,};

        for (int i = 0; i < CHUNKS; i++) {
            Models.Mesh mesh = meshes[i % meshes.length];
            GameModelInstance inst = Models.getModelInstance(mesh, color, 1f);
            Piece p = new Piece(inst);
            p.size = computeHeight(inst);
            missilePieces.add(p);
        }
    }

    public void spawn(boolean tank, float x, float z) {

        origin.set(x, 0.5f, z);
        finished = false;

        pieces = tank ? tankPieces : missilePieces;

        for (Piece p : pieces) {

            float originalSize = p.size;
            p.reset();
            p.size = originalSize;

            // Place each piece so its bottom sits above the ground plane
            float half = p.size * 0.5f;
            float startY = Math.max(origin.y, GROUND_Y + half + 0.01f);

            // Small spatial jitter (scaled to size but clamped)
            float jitter = Math.min(half * 0.05f, 80f);
            float jx = ((float) Math.random() * 2f - 1f) * jitter;
            float jz = ((float) Math.random() * 2f - 1f) * jitter;
            float jy = ((float) Math.random() * 2f - 1f) * (jitter * 0.15f);

            p.pos.set(origin.x + jx, startY + jy, origin.z + jz);

            // Aim elevation
            float az = (float) (Math.random() * Math.PI * 2.0);  // 0..2π
            float meanDeg = 65f;   // centered near 65°
            float spreadDeg = 20f;    //  variation
            float elDeg = meanDeg + (float) ((Math.random() * 2.0 - 1.0) * spreadDeg);
            // clamp to a sensible range so nothing goes too flat or too vertical
            if (elDeg < 35f) {
                elDeg = 35f;
            }
            if (elDeg > 80f) {
                elDeg = 80f;
            }
            float el = (float) Math.toRadians(elDeg);

            float cosEl = (float) Math.cos(el);
            float sinEl = (float) Math.sin(el);
            float dirX = (float) (Math.cos(az) * cosEl);
            float dirY = sinEl;
            float dirZ = (float) (Math.sin(az) * cosEl);

            // Launch speed
            float speed = INITIAL_SPEED_MIN + (float) Math.random() * (INITIAL_SPEED_MAX - INITIAL_SPEED_MIN);
            float sizeScale = 1f + Math.min(p.size / 300f, 0.35f); // up to +35% for big chunks
            speed *= sizeScale;

            // Initial velocity
            p.vel.set(dirX * speed, dirY * speed, dirZ * speed);

            float ax = 0f, ay = 1f, azz = 0f; // force Y axis spin only
            p.axis.set(ax, ay, azz);

            float spin = 240f + (float) Math.random() * 600f;
            if (Math.random() < 0.5f) {
                spin = -spin;
            }
            p.spinDeg = spin;

            p.inst.transform.idt();
            p.inst.transform.setTranslation(p.pos);
        }
    }

    public void update(float dt, TankSpawn spawn) {
        boolean looksUninitialized = true;
        for (Piece p : pieces) {
            if (!p.pos.isZero() || !p.vel.isZero() || p.spinDeg != 0f || p.groundedTime != 0f) {
                looksUninitialized = false;
                break;
            }
        }
        if (looksUninitialized) {
            finished = true;
            return;
        }
        if (finished) {
            return;
        }

        boolean allGrounded = true;

        for (Piece p : pieces) {
            float half = p.size * 0.5f;

            if (!p.grounded) {

                p.vel.y += GRAVITY * dt;

                float dragFactor = (float) Math.pow(DRAG, dt);
                p.vel.scl(dragFactor);

                p.pos.mulAdd(p.vel, dt);

                // Ground contact with tolerance
                if (p.pos.y - half <= GROUND_Y + GROUND_EPS) {
                    // Clamp to ground plane
                    p.pos.y = GROUND_Y + half;

                    // Bounce only if coming down
                    if (p.vel.y < 0f) {
                        p.vel.y = -p.vel.y * RESTITUTION;
                    }

                    // Friction & spin damping on impact
                    p.vel.x *= FRICTION;
                    p.vel.z *= FRICTION;
                    p.spinDeg *= SPIN_DAMP;

                    // Sleep condition: small vertical AND small horizontal velocity
                    float h2 = p.vel.x * p.vel.x + p.vel.z * p.vel.z;
                    if (Math.abs(p.vel.y) < STOP_BOUNCE_VY && h2 < HVEL_EPS * HVEL_EPS) {
                        p.vel.setZero();
                        p.spinDeg = 0f;
                        p.grounded = true;
                    }
                }

                // Extra safety: if we're essentially on the ground and barely moving, sleep it
                if (!p.grounded && (p.pos.y - half) <= GROUND_EPS && p.vel.len2() < SLEEP_VEL_EPS * SLEEP_VEL_EPS) {
                    p.pos.y = GROUND_Y + half;
                    p.vel.setZero();
                    p.spinDeg = 0f;
                    p.grounded = true;
                }

                if (!p.grounded) {
                    allGrounded = false;
                }
            } else {
                p.groundedTime += dt;
            }

            if (dt > 0f && p.spinDeg != 0f) {
                p.inst.transform.rotate(p.axis, p.spinDeg * dt);
            }
            p.inst.transform.setTranslation(p.pos);
        }

        if (allGrounded) {
            boolean done = true;
            for (Piece p : pieces) {
                if (p.groundedTime < TTL_AFTER_SETTLE) {
                    done = false;
                    break;
                }
            }
            finished = done;

            if (done && spawn != null) {
                spawn.spawn();
            }
        }
    }

    public void render(Camera cam, ModelBatch batch, Environment env) {
        if (finished) {
            return;
        }
        for (Piece p : pieces) {
            nearestWrappedPos(p.inst, cam.position.x, cam.position.z, TMP1);
            if (cam.frustum.pointInFrustum(TMP1)) {
                p.inst.transform.val[Matrix4.M03] = TMP1.x;
                p.inst.transform.val[Matrix4.M13] = TMP1.y;
                p.inst.transform.val[Matrix4.M23] = TMP1.z;
                batch.render(p.inst, env);
            }
        }
    }

    private static float computeHeight(ModelInstance inst) {
        BoundingBox TMP_BB = new BoundingBox();
        inst.calculateBoundingBox(TMP_BB);
        TMP_BB.mul(inst.transform);
        TMP_BB.getDimensions(TMP1);
        return TMP1.y;
    }

}
