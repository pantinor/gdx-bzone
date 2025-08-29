package bzone;

import static bzone.BattleZone.SCREEN_HEIGHT;
import static bzone.BattleZone.SCREEN_WIDTH;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import jakarta.xml.bind.DatatypeConverter;
import java.util.ArrayList;
import java.util.List;

public class Background {

    private static final String LAND1_HEX = "40000000E01F2060185C2800506000002060E01F20602000C07FC01F000040008060C01F4060000020002000C07F2000E01FE01F4060F01F4060F01F6060A0003000F41F05E0E05AFC5AFA5AFD1FF4FF0300F4FFF71F0CE0FD1F0CE003000CE009000CE0E346E046FE460C00F5FF0300F35FFC1FF15F5B5CF51FFB5FF31FFE5FF11F064003001B00A05EFF1F06A00B0006A0FF1FFCBF0200FFBF094AFD1FFDBF030001A00100FDBFA043010001406A1F070000C0";
    private static final String LAND2_HEX = "0000200030004060D01F20603000E01FF01F206020004060C01FA0602000807F2000E07FE01FC01FE01F6060000080002000A060E01F000000C0";
    private static final String LAND3_HEX = "2000000000004060E01F00002000C07F0000400020004060E01FC01FE01F6060200020602000C07F00002060E01F206010002060F01F2060E01FA07F2000600020004060E01F40601000A00FF01F206010002060D01F0000080060601800C07F10002060F01F4060E81FE07F2800E07FF01F400000002060E01F000000C0";
    private static final String LAND4_HEX = "20000000E01F40602000C01FE01F80600000A00020006060E01F20602000E01F20002060C01F2060000020004000C07FE01F200020002060E01F2060E01F000000C0";
    private static final String LAND5_HEX = "2000000020004060C01F2060000020004000C07FE01F200010002060D01F6060000080002000A060E01F000000C0";
    private static final String LAND6_HEX = "2000000000004060E01FE0603000E07FE01FC07FF01F600040006060D81F4060185460004060F91F036005000560FA1F036008000560A01F000000C0";
    private static final String LAND7_HEX = "60000000A01F406040006060C01F4060000040004000807FE01F400020004060C01F80602000C01F10004060D01F20603000E01FD01F606000C0";
    private static final String LAND8_HEX = "0000C0002000E060E01F40601000E01F30004060C01F000000C0";

    // background 
    private static final int ANGLES = 512;
    private static final float UNITS_PER_ANGLE = -8f;
    private static final float SEG_W_UNITS = 512f;
    private static final int SEG_COUNT = 8;
    private static final float STRIP_UNITS = SEG_W_UNITS * SEG_COUNT;
    private int angle9 = 0;
    private final int horizonAdj = 0;
    private static final List<List<Vector>> LAND = landScapeVectors();

    // volcano particles  
    private static final int VOLCANO_SEG_INDEX = 5;
    private static final int VOLCANO_PARTICLES = 5;
    private static final int VOLCANO_TOP_Y_UNITS = 94; // top of volcano is +94 units above horizon
    private static final int VOLCANO_GROUND_Y = -45; // ground is -94 units below the origin
    private static final int VOLCANO_X_OFFSET_UNITS = -5; // offset from RIGHT edge of the section; 0 = right edge

    private final VolcanoParticle[] volcanoParticles = new VolcanoParticle[VOLCANO_PARTICLES];

    public Background() {
        for (int i = 0; i < volcanoParticles.length; i++) {
            volcanoParticles[i] = new VolcanoParticle();
        }
    }

    private static final class Vector {

        final int dx, dy, intensity;
        final boolean shortVec;

        Vector(int dx, int dy, int in, boolean s) {
            this.dx = dx;
            this.dy = dy;
            this.intensity = in;
            this.shortVec = s;
        }

        @Override
        public String toString() {
            return (shortVec ? "SVEC" : "VCTR") + String.format(" dx=%+d dy=%+d in=%d", dx, dy, intensity);
        }
    }

    private static int signExtend(int v, int bits) {
        int mask = 1 << (bits - 1);
        return (v ^ mask) - mask;
    }

    private static int rd16LE(byte[] b, int off) {
        return (b[off] & 0xFF) | ((b[off + 1] & 0xFF) << 8);
    }

    private static List<Vector> parseLandscapeVectors(String hex) {
        byte[] b = DatatypeConverter.parseHexBinary(hex);
        List<Vector> out = new ArrayList<>();
        int pc = 0;
        int curInt = 0;

        while (pc + 2 <= b.length) {
            int w0 = rd16LE(b, pc);

            // VRTS?
            if (w0 == 0xC000) {
                break;
            }

            // SVEC? (bit 14 set)
            if ((w0 & 0x4000) != 0) {
                // dy: bits 12..8 (5-bit signed) * 2
                int dy5 = (w0 >>> 8) & 0x1F;
                int dy = signExtend(dy5, 5) * 2;

                // dx: bits 4..0 (5-bit signed) * 2
                int dx5 = (w0) & 0x1F;
                int dx = signExtend(dx5, 5) * 2;

                out.add(new Vector(dx, dy, curInt, true));
                pc += 2;
                continue;
            }

            // VCTR (long): needs a second word
            if (pc + 4 > b.length) {
                break; // safety
            }
            int w1 = rd16LE(b, pc + 2);

            // dy: 10-bit signed from w0 (bits 9..0) -> low8 + bits 9..8 from w0[11..10]
            int dy10 = ((w0 >>> 10) & 0x03) << 8 | (w0 & 0xFF);
            int dy = signExtend(dy10, 10);

            // dx: 10-bit signed from w1 (bits 9..0) -> low8 + bits 9..8 from w1[11..10]
            int dx10 = ((w1 >>> 10) & 0x03) << 8 | (w1 & 0xFF);
            int dx = signExtend(dx10, 10);

            // intensity (latched)
            curInt = (w1 >>> 12) & 0x0F;

            out.add(new Vector(dx, dy, curInt, false));
            pc += 4;
        }

        return out;
    }

    private static List<List<Vector>> landScapeVectors() {
        return List.of(
                parseLandscapeVectors(LAND1_HEX),
                parseLandscapeVectors(LAND2_HEX),
                parseLandscapeVectors(LAND3_HEX),
                parseLandscapeVectors(LAND4_HEX),
                parseLandscapeVectors(LAND5_HEX),
                parseLandscapeVectors(LAND6_HEX),
                parseLandscapeVectors(LAND7_HEX),
                parseLandscapeVectors(LAND8_HEX)
        );
    }

    private void drawLandscapeSection(ShapeRenderer sr, List<Vector> section, float originX, float originY, float unit2px) {
        float x = originX, y = originY;
        for (Vector v : section) {
            float nx = x + v.dx * unit2px;
            float ny = y + v.dy * unit2px;

            if (v.intensity > 0) {
                float level = Math.min(1f, Math.max(0f, v.intensity / 3f));// 0..1 brightness
                sr.setColor(0f, level, 0f, 1f); // <-- green only
                sr.line(x, y, nx, ny);
            }
            x = nx;
            y = ny;
        }
    }

    public void drawBackground2D(ShapeRenderer sr, float hd) {

        angle9 = (int) (hd / 360f * ANGLES) % ANGLES;

        sr.begin(ShapeRenderer.ShapeType.Line);

        final int w = SCREEN_WIDTH;
        final int h = SCREEN_HEIGHT;

        final float unit2px = w / 1024f;
        final float segWpx = SEG_W_UNITS * unit2px;

        final float horizonY = h * 0.5f - ((horizonAdj >> 4) * unit2px);

        sr.setColor(0f, 0.7f, 0f, 1f);
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
            drawLandscapeSection(sr, LAND.get(idx), sx, horizonY, unit2px);
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
