package bzone;

import static bzone.BattleZone.PLAYER_Y;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;

public class Spatter {

    private final float frameTime = 0.1f;
    private final float scale = 10f;
    private final Vector3 origin = new Vector3();
    private boolean finished = true;
    private float timeAccum = 0f;

    private static final int COUNT = 12;

    private final Vector3[] particles = new Vector3[COUNT];

    public Spatter() {
        for (int i = 0; i < particles.length; i++) {
            particles[i] = new Vector3();
        }
    }

    public void spawn(float x, float z) {
        this.origin.set(x, PLAYER_Y, z);
        int[][] initPositions = createInitPos();
        for (int i = 0; i < particles.length; i++) {
            float px = origin.x + initPositions[i][0] * scale;
            float py = origin.y + initPositions[i][1] * scale;
            float pz = origin.z + initPositions[i][2] * scale;
            particles[i].set(px, py, pz);
        }
        finished = false;
        timeAccum = 0f;
    }

    private static int[][] createInitPos() {
        int[][] pts = new int[COUNT][3];
        int idx = 0;

        idx = fillQuadrant(pts, idx, +1, +1);
        idx = fillQuadrant(pts, idx, -1, +1);
        idx = fillQuadrant(pts, idx, -1, -1);
        idx = fillQuadrant(pts, idx, +1, -1);

        return pts;
    }

    private static int fillQuadrant(int[][] pts, int idx, int sx, int sy) {
        for (int k = 0; k < 3; k++) {
            int x = MathUtils.random(1, 10) * sx;
            int y = MathUtils.random(1, 10) * sy;
            int z = MathUtils.random(1, 10) * MathUtils.randomSign();
            pts[idx][0] = x;
            pts[idx][1] = y;
            pts[idx][2] = z;
            idx++;
        }
        return idx;
    }

    public void update(float dt) {
        if (finished) {
            return;
        }

        timeAccum += dt;

        final float speed = 600f;
        final float maxRadius = 800f;  // finish when all particles are beyond this radius

        final float stepDist = speed * frameTime;

        while (timeAccum >= frameTime) {
            timeAccum -= frameTime;

            boolean allPastMax = true;

            for (int i = 0; i < particles.length; i++) {

                // 3D spoke direction from origin to current particle (x,y,z)
                Vector3 p = particles[i];
                float dx = p.x - origin.x;
                float dy = p.y - origin.y;
                float dz = p.z - origin.z;

                float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (len > 0f) {
                    dx /= len;
                    dy /= len;
                    dz /= len;   // normalize to unit vector
                }

                // Advance in 3D; constant speed
                p.add(dx * stepDist, dy * stepDist, dz * stepDist);

                // Distance check from spawn origin in XZ
                Vector3 pos = particles[i];
                float ox = pos.x - origin.x;
                float oy = pos.y - origin.y;
                float r2 = ox * ox + oy * oy;
                if (r2 < maxRadius * maxRadius) {
                    allPastMax = false;
                }
            }

            if (allPastMax) {
                finished = true;
                break;
            }
        }
    }

    public void render(ShapeRenderer sr) {
        if (finished) {
            return;
        }

        final float W = 4, H = 4, D = 4;
        final float hx = W * 0.5f, hy = H * 0.5f, hz = D * 0.5f;

        sr.begin(ShapeRenderer.ShapeType.Line);
        sr.setColor(Color.GREEN);
        for (Vector3 p : particles) {
            sr.box(p.x - hx, p.y - hy, p.z - hz, W, H, D);
        }
        sr.end();
    }

}
