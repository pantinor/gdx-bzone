package bzone;

import static bzone.BattleZone.SCREEN_HEIGHT;
import static bzone.BattleZone.SCREEN_WIDTH;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import java.util.List;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.math.Matrix4;

public class Background {

    private static final int ANGLES = 512;
    private static final float UNITS_PER_ANGLE = -8f;
    private static final float SEG_W_UNITS = 512f;
    private static final int SEG_COUNT = 8;
    private static final float STRIP_UNITS = SEG_W_UNITS * SEG_COUNT;
    private int angle9 = 0;
    private final int horizonAdj = 0;

    private final List<ModelInstance> sections;

    private static final int VOLCANO_SEG_INDEX = 5;
    private static final int VOLCANO_PARTICLES = 5;
    private static final int VOLCANO_TOP_Y_UNITS = 94; // top of volcano is +94 units above horizon
    private static final int VOLCANO_GROUND_Y = -45; // ground is -94 units below the origin
    private static final int VOLCANO_X_OFFSET_UNITS = -5; // offset from RIGHT edge of the section; 0 = right edge

    private final VolcanoParticle[] volcanoParticles = new VolcanoParticle[VOLCANO_PARTICLES];

    public Background() {

        sections = Models.loadBackgroundObjects("assets/data/background.obj", 1);

        for (int i = 0; i < volcanoParticles.length; i++) {
            volcanoParticles[i] = new VolcanoParticle();
        }
    }

    public void drawBackground2D(ShapeRenderer sr, ModelBatch batch, Environment env, float hd) {

        angle9 = (int) (hd / 360f * ANGLES) % ANGLES;

        sr.begin(ShapeRenderer.ShapeType.Line);

        final int w = SCREEN_WIDTH;
        final int h = SCREEN_HEIGHT;

        final float unit2px = w / 1024f;
        final float segWpx = SEG_W_UNITS * unit2px;

        final float horizonY = h * 0.5f - ((horizonAdj >> 4) * unit2px);

        sr.setColor(0f, 1f, 0f, 1f);
        sr.line(0, horizonY, w, horizonY);

        // Scroll: 8 units per angle, wrap at 4096
        int scrollUnits = (int) ((angle9 * UNITS_PER_ANGLE) % STRIP_UNITS);
        if (scrollUnits < 0) {
            scrollUnits += STRIP_UNITS;
        }

        int segIndex = (int) (scrollUnits / SEG_W_UNITS) & 7;
        float offsetInSegUnits = scrollUnits % SEG_W_UNITS;
        float startX = -offsetInSegUnits * unit2px;

        for (int i = 0; i < 3; i++) {
            int idx = (segIndex + i) & 7;
            float sx = startX + i * segWpx;

            Matrix4 xform = sections.get(idx).transform;
            xform.setToTranslationAndScaling(sx, horizonY, 0f, unit2px, unit2px, 1f);
            
            batch.render(sections.get(idx), env);
        }

        // volcano is tied to a specific landscape segment; origin is at the RIGHT edge of that segment
        int offsetIdx = (VOLCANO_SEG_INDEX - segIndex) & 7;       // how many segments ahead
        float volcanoRightEdgeX = startX + offsetIdx * segWpx + segWpx;

        float originXpx = volcanoRightEdgeX + VOLCANO_X_OFFSET_UNITS * unit2px;
        float originYpx = horizonY + VOLCANO_TOP_Y_UNITS * unit2px;

        updateVolcanoParticles();
        drawVolcanoParticles(sr, originXpx, originYpx, unit2px, w);

        sr.end();
    }

    private static final class VolcanoParticle {

        int ttl;   // 0..31
        float x;     // position in "vector units" relative to the volcano origin
        float y;
        float vx;    // signed velocity in units/frame (ROM is 8-bit signed)
        float vy;    // signed velocity in units/frame (gravity lowers this by 1 each update)

        void reset() {
            ttl = 0;
            x = y = vx = vy = 0;
        }
    }

    private void updateVolcanoParticles() {
        for (int i = 0; i < volcanoParticles.length; i++) {
            VolcanoParticle p = volcanoParticles[i];

            if (p.ttl <= 0) {
                if (MathUtils.random(7) == 0) {
                    p.ttl = 0x1f;
                    int speed = MathUtils.random(1, 4);
                    boolean goRight = MathUtils.randomBoolean();
                    p.vx = goRight ? speed : -(speed + 1);
                    p.vy = MathUtils.random(5, 12);
                    p.x = 0;
                    p.y = 0;
                }
                continue;
            }

            p.ttl--;
            if (p.ttl == 0) {
                p.x = p.y = 0;
                continue;
            }

            float AIR_SLOW = 0.55f;

            p.vy -= 1f * AIR_SLOW;
            p.y += p.vy * AIR_SLOW;

            if (p.y < VOLCANO_GROUND_Y) {
                p.reset();
                continue;
            }

            // horizontal unchanged
            p.x += p.vx;
        }
    }

    private void drawVolcanoParticles(ShapeRenderer sr, float originXpx, float originYpx, float unit2px, int screenW) {
        sr.end();
        sr.begin(ShapeRenderer.ShapeType.Filled);

        for (VolcanoParticle p : volcanoParticles) {
            if (p.ttl <= 0) {
                continue;
            }

            float px = originXpx + p.x * unit2px;
            float py = originYpx + p.y * unit2px;

            // quick cull
            if (px < -10 || px > screenW + 10) {
                continue;
            }

            // brightness from ttl
            float g = MathUtils.clamp(((p.ttl >> 2) / 7f) * 2f, 0f, 2f);
            sr.setColor(0, g, 0, 1f);

            float r = MathUtils.random(1f, 2f);
            sr.circle(px, py, r);
        }

        // Restore Line mode for the rest of the background pass
        sr.end();
        sr.begin(ShapeRenderer.ShapeType.Line);
    }

}
