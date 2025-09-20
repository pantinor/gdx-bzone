import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import static java.lang.Math.*;

public class Stellar7MeshDump {

    // === CONFIG ===
    private static final Path BASE_DIR   = Paths.get("C:\\Users\\panti\\Downloads\\a2-stellar7"); // folder with BRIEF.ST, LEV1, LEV7
    private static final Path OUTPUT_OBJ = Paths.get("stellar7.obj");
    private static final int  END = 0x80;
    // overall scale applied like your previous code (you used 8 in the sample)
    private static final int  DEFAULT_SCALE = 8;

    // Mesh entry: which file, human name, offset, (optional per-mesh scale)
    private record MeshRef(String file, String name, int offsetHex, Integer scaleOpt) {
        int scale() { return (scaleOpt != null) ? scaleOpt : DEFAULT_SCALE; }
    }

    private static final List<MeshRef> MESHES = List.of(
        // BRIEF.ST
        new MeshRef("BRIEF.ST","Sandsled",                0x0080,null),
        new MeshRef("BRIEF.ST","Laser Tank",              0x00E1,null),
        new MeshRef("BRIEF.ST","Hovercraft",              0x0163,null),
        new MeshRef("BRIEF.ST","Prowler",                 0x01EA,null),
        new MeshRef("BRIEF.ST","Heavy Tank",              0x0264,null),
        new MeshRef("BRIEF.ST","Stalker",                 0x02F2,null),
        new MeshRef("BRIEF.ST","Laser Battery",           0x036E,null),
        new MeshRef("BRIEF.ST","Gun Battery",             0x03D6,null),
        new MeshRef("BRIEF.ST","Pulsar",                  0x0443,null),
        new MeshRef("BRIEF.ST","Skimmer",                 0x048D,null),
        new MeshRef("BRIEF.ST","Stinger",                 0x04E0,null),
        new MeshRef("BRIEF.ST","Seeker",                  0x0517,null),
        new MeshRef("BRIEF.ST","Obstacle",                0x0587,null),
        new MeshRef("BRIEF.ST","Fuelbay",                 0x05B9,null),
        new MeshRef("BRIEF.ST","Warplink",                0x0612,null),

        // LEV1
        new MeshRef("LEV1","Explosion 0",                 0x0080,null),
        new MeshRef("LEV1","Explosion 1",                 0x00BA,null),
        new MeshRef("LEV1","Explosion 2",                 0x012C,null),
        new MeshRef("LEV1","Explosion 3",                 0x019E,null),
        new MeshRef("LEV1","Explosion 4",                 0x0210,null),
        new MeshRef("LEV1","Explosion 5",                 0x0282,null),
        new MeshRef("LEV1","Explosion 6",                 0x02F4,null),
        new MeshRef("LEV1","Explosion 7",                 0x0366,null),
        new MeshRef("LEV1","Impact 0",                    0x03D8,null),
        new MeshRef("LEV1","Impact 1",                    0x03FB,null),
        new MeshRef("LEV1","Impact 2",                    0x042D,null),
        new MeshRef("LEV1","Impact 3",                    0x045F,null),
        new MeshRef("LEV1","Impact 4",                    0x0491,null),
        new MeshRef("LEV1","Impact 5",                    0x04C3,null),
        new MeshRef("LEV1","Impact 6",                    0x04F5,null),
        new MeshRef("LEV1","Impact 7",                    0x0527,null),
        new MeshRef("LEV1","Sandsled",                    0x0559,null),
        new MeshRef("LEV1","Hovercraft",                  0x05BA,null),
        new MeshRef("LEV1","Skimmer",                     0x0641,null),
        new MeshRef("LEV1","Laser Projectile",            0x0694,null),
        new MeshRef("LEV1","Cannon Projectile",           0x06C6,null),
        new MeshRef("LEV1","Obstacle",                    0x06E7,null),
        new MeshRef("LEV1","Warplink",                    0x0719,null),

        // LEV7
        new MeshRef("LEV7","Explosion 0",                 0x0080,null),
        new MeshRef("LEV7","Explosion 1",                 0x00BA,null),
        new MeshRef("LEV7","Explosion 2",                 0x012C,null),
        new MeshRef("LEV7","Explosion 3",                 0x019E,null),
        new MeshRef("LEV7","Explosion 4",                 0x0210,null),
        new MeshRef("LEV7","Explosion 5",                 0x0282,null),
        new MeshRef("LEV7","Explosion 6",                 0x02F4,null),
        new MeshRef("LEV7","Explosion 7",                 0x0366,null),
        new MeshRef("LEV7","Impact 0",                    0x03D8,null),
        new MeshRef("LEV7","Impact 1",                    0x03FB,null),
        new MeshRef("LEV7","Impact 2",                    0x042D,null),
        new MeshRef("LEV7","Impact 3",                    0x045F,null),
        new MeshRef("LEV7","Impact 4",                    0x0491,null),
        new MeshRef("LEV7","Impact 5",                    0x04C3,null),
        new MeshRef("LEV7","Impact 6",                    0x04F5,null),
        new MeshRef("LEV7","Impact 7",                    0x0527,null),
        new MeshRef("LEV7","Stalker",                     0x0559,null),
        new MeshRef("LEV7","Gir Draxon",                  0x05D5,null),
        new MeshRef("LEV7","Pulsar",                      0x0684,null),
        new MeshRef("LEV7","Stinger",                     0x06CE,null),
        new MeshRef("LEV7","Guiser",                      0x0705,null),
        new MeshRef("LEV7","Laser Projectile",            0x0737,null),
        new MeshRef("LEV7","Cannon Projectile",           0x0769,null),
        new MeshRef("LEV7","Heavy Cannon Projectile",     0x078A,null),
        new MeshRef("LEV7","Obstacle",                    0x07AB,null)
    );

    public static void main(String[] args) throws Exception {
        // read ROMs once
        Map<String, byte[]> roms = new HashMap<>();
        for (String fname : Set.of("BRIEF.ST","LEV1","LEV7")) {
            Path p = BASE_DIR.resolve(fname);
            roms.put(fname, Files.readAllBytes(p));
        }

        // build OBJ
        String nl = System.lineSeparator();
        StringBuilder obj = new StringBuilder(1 << 20);
        obj.append("# Stellar 7 OBJ export (vertices/edges) ").append(new Date()).append(nl).append(nl);

        int globalVBase = 0; // 0-based count of vertices already written

        for (MeshRef m : MESHES) {
            byte[] rom = roms.get(m.file);
            if (rom == null) continue;

            ParsedMesh mesh = parseMeshAt(rom, m.offsetHex, m.scale());

            // header
            obj.append("o ").append(safeName(m.name)).append(nl);

            // vertices (scaled like your previous code)
            for (float[] v : mesh.verts) {
                obj.append("v ")
                   .append(fmt(v[0])).append(' ')
                   .append(fmt(v[1])).append(' ')
                   .append(fmt(v[2])).append(nl);
            }

            // edges as OBJ lines (remember: OBJ is 1-based)
            for (int[] e : mesh.edges) {
                int a = globalVBase + e[0] + 1;
                int b = globalVBase + e[1] + 1;
                obj.append("l ").append(a).append(' ').append(b).append(nl);
            }
            obj.append(nl);

            globalVBase += mesh.verts.size();
        }

        try (BufferedWriter w = Files.newBufferedWriter(OUTPUT_OBJ)) {
            w.write(obj.toString());
        }
        System.out.println("Wrote OBJ: " + OUTPUT_OBJ);
    }

    // === parsing exactly like your earlier implementation ===
    private static ParsedMesh parseMeshAt(byte[] rom, int offset, int scale) {
        int p = offset;

        // --- vertices (dist, angle, y) until END ---
        List<float[]> verts = new ArrayList<>();
        while (p < rom.length && (rom[p] & 0xFF) != END) {
            if (p + 2 >= rom.length) break; // safety
            int dist  = rom[p++] & 0xFF;
            int angle = rom[p++] & 0xFF;
            int y     = (byte) rom[p++]; // signed

            double rads = (angle / 128.0) * Math.PI;
            double x = cos(rads) * dist;
            double z = sin(rads) * dist;

            // scale like your code (integer scale, but we keep float in OBJ)
            verts.add(new float[] {
                (float) (x * scale),
                (float) (y * scale),
                (float) (z * scale)
            });
        }
        if (p < rom.length) p++; // skip END

        // --- edges (pairs of vertex indices) until END ---
        List<int[]> edges = new ArrayList<>();
        while (p < rom.length && (rom[p] & 0xFF) != END) {
            if (p + 1 >= rom.length) break; // safety
            int v0 = rom[p++] & 0xFF;
            int v1 = rom[p++] & 0xFF;
            // bounds-check but keep lenient
            if (v0 >= 0 && v0 < verts.size() && v1 >= 0 && v1 < verts.size()) {
                edges.add(new int[]{v0, v1});
            }
        }
        // (optional) if (p < rom.length) p++; // could skip the trailing END if needed

        return new ParsedMesh(verts, edges);
    }

    // === helpers ===
    private static String fmt(float v) { return String.format(Locale.US, "%.6f", v); }
    private static String safeName(String s) { return s.replaceAll("[\\s]+", "_"); }

    private static final class ParsedMesh {
        final List<float[]> verts;
        final List<int[]> edges;
        ParsedMesh(List<float[]> v, List<int[]> e) { this.verts = v; this.edges = e; }
    }
}
