package bzone;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector3;

import java.util.ArrayList;
import java.util.List;

public class Spatter {

    private final float frameTime = 0.1f;
    private final float pointSize = 8;
    private final Color color = Color.GREEN;
    private final Vector3 origin = new Vector3();

    private int frameIdx = 0;
    private float tAcc = 0f;
    private boolean finished = false;

    private final Vector3 rotationAxis = new Vector3(1, 1, 0);
    private Vector3[][] rotatedVerts;

    public Spatter(float ax, float ay, float az) {
        this.rotationAxis.set(ax, ay, az).nor();
        buildRotatedCache();
    }

    private static final Frame[] FRAMES = new Frame[]{
        Frame.parse("W1 V 9 -52 -360 0 -36 -360 36 0 -360 52 36 -360 36 52 -360 0 36 -360 -36 0 -360 -52 -36 -360 -36 0 0 0 E 0 P 8 0 1 2 3 4 5 6 7"),
        Frame.parse("W1 V 9 -100 -400 0 -72 -400 72 0 -400 100 72 -400 72 100 -400 0 72 -400 -72 0 -400 -100 -72 -400 -72 0 0 0 E 0 P 8 0 1 2 3 4 5 6 7"),
        Frame.parse("W1 V 9 -152 -440 0 -108 -440 108 0 -440 152 108 -440 108 152 -440 0 108 -440 -108 0 -440 -152 -108 -440 -108 0 0 0 E 0 P 8 0 1 2 3 4 5 6 7"),
        Frame.parse("W1 V 9 -200 -480 0 -144 -480 144 0 -480 200 144 -480 144 200 -480 0 144 -480 -144 0 -480 -200 -144 -480 -144 0 0 0 E 0 P 8 0 1 2 3 4 5 6 7"),
        Frame.parse("W1 V 9 -252 -520 0 -176 -520 176 0 -520 252 176 -520 176 252 -520 0 176 -520 -176 0 -520 -252 -176 -520 -176 0 0 0 E 0 P 8 0 1 2 3 4 5 6 7"),
        Frame.parse("W1 V 9 -300 -560 0 -212 -560 212 0 -560 300 212 -560 212 300 -560 0 212 -560 -212 0 -560 -300 -212 -560 -212 0 0 0 E 0 P 8 0 1 2 3 4 5 6 7"),
        Frame.parse("W1 V 9 -352 -600 0 -264 -600 264 0 -600 352 264 -600 264 352 -600 0 264 -600 -264 0 -600 -352 -264 -600 -264 0 0 0 E 0 P 8 0 1 2 3 4 5 6 7"),
        Frame.parse("W1 V 9 -400 -640 0 -284 -640 284 0 -640 400 284 -640 284 400 -640 0 284 -640 -284 0 -640 -400 -284 -640 -284 0 0 0 E 0 P 8 0 1 2 3 4 5 6 7"),};

    public void spawn(float x, float z) {
        this.origin.set(x, 0.6f, z);
        frameIdx = 0;
        tAcc = 0f;
        finished = false;
    }

    public void update(float dt) {
        if (finished) {
            return;
        }

        if (dt > 0.1f) {
            dt = 0.1f;
        }

        tAcc += dt;
        while (tAcc >= frameTime) {
            tAcc -= frameTime;
            frameIdx++;
            if (frameIdx >= FRAMES.length) {
                frameIdx = FRAMES.length - 1;
                finished = true;
                break;
            }
        }
    }

    public void render(ShapeRenderer sr) {

        if (finished) {
            return;
        }

        float alphaMul = 1f - (frameIdx / (float) (FRAMES.length - 1));
        float prevA = color.a;
        float a = Math.max(0f, Math.min(1f, prevA * (0.35f + 0.65f * alphaMul))); // keep some visibility

        sr.begin(ShapeRenderer.ShapeType.Filled);
        sr.setColor(color.r, color.g, color.b, a);

        Frame f = FRAMES[frameIdx];
        Vector3[] verts = (rotatedVerts != null) ? rotatedVerts[frameIdx] : f.vertices;

        for (int idx : f.drawIndices) {
            Vector3 p = verts[idx];
            sr.box(origin.x + p.x, origin.y + p.y, origin.z + p.z, pointSize, pointSize, pointSize);
        }

        sr.end();

        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    private void buildRotatedCache() {
        rotatedVerts = new Vector3[FRAMES.length][];
        for (int i = 0; i < FRAMES.length; i++) {
            Vector3[] src = FRAMES[i].vertices;
            Vector3[] dst = new Vector3[src.length];
            for (int j = 0; j < src.length; j++) {
                dst[j] = new Vector3(src[j]).rotate(rotationAxis, 90);
            }
            rotatedVerts[i] = dst;
        }
    }

    private static class Frame {

        final Vector3[] vertices;
        final int[] drawIndices;

        private Frame(Vector3[] verts, int[] draw) {
            this.vertices = verts;
            this.drawIndices = draw;
        }

        static Frame parse(String s) {
            String[] tok = s.trim().split("\\s+");
            int i = 0;

            while (i < tok.length && !tok[i].equalsIgnoreCase("V")) {
                i++;
            }
            if (i >= tok.length - 1) {
                throw new IllegalArgumentException("No vertex count: " + s);
            }
            int vCount = Integer.parseInt(tok[++i]);
            i++;

            List<Vector3> verts = new ArrayList<>(vCount);
            for (int v = 0; v < vCount; v++) {
                float x = Float.parseFloat(tok[i++]);
                float y = Float.parseFloat(tok[i++]);
                float z = Float.parseFloat(tok[i++]);
                verts.add(new Vector3(x, y, z));
            }

            while (i < tok.length && !tok[i].equalsIgnoreCase("P")) {
                i++;
            }
            if (i >= tok.length - 1) {
                throw new IllegalArgumentException("No P section: " + s);
            }
            int pCount = Integer.parseInt(tok[++i]);
            i++;

            int[] draw = new int[pCount];
            for (int p = 0; p < pCount; p++) {
                draw[p] = Integer.parseInt(tok[i++]);
            }

            return new Frame(verts.toArray(new Vector3[0]), draw);
        }
    }
}
