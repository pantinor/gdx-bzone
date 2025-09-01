
import java.nio.file.*;
import java.util.*;
import static java.lang.Math.*;

public class Stellar7MeshDump {

    public static void main(String[] args) throws Exception {
        byte[] rom = Files.readAllBytes(Paths.get("C:\\Users\\panti\\Downloads\\a2-stellar7\\LEV7"));
        int offset = 0x05D5;
        int scale = 8;
        System.out.println(meshEnumLine(rom, offset, "Gir Draxon", scale));
    }

    private static final int END = 0x80;

    /**
     * Stellar 7 meshes discovered in *.dis65 files (Visualizer ident:
     * "stellar7-mesh"). Offsets refer to the mesh data block within the
     * corresponding ROM file.
     *
     * <pre>
     * BRIEF.ST : Sandsled                @ 0x0080 (128)
     * BRIEF.ST : Laser Tank              @ 0x00E1 (225)
     * BRIEF.ST : Hovercraft              @ 0x0163 (355)
     * BRIEF.ST : Prowler                 @ 0x01EA (490)
     * BRIEF.ST : Heavy Tank              @ 0x0264 (612)
     * BRIEF.ST : Stalker                 @ 0x02F2 (754)
     * BRIEF.ST : Laser Battery           @ 0x036E (878)
     * BRIEF.ST : Gun Battery             @ 0x03D6 (982)
     * BRIEF.ST : Pulsar                  @ 0x0443 (1091)
     * BRIEF.ST : Skimmer                 @ 0x048D (1165)
     * BRIEF.ST : Stinger                 @ 0x04E0 (1248)
     * BRIEF.ST : Seeker                  @ 0x0517 (1303)
     * BRIEF.ST : Obstacle                @ 0x0587 (1415)
     * BRIEF.ST : Fuelbay                 @ 0x05B9 (1465)
     * BRIEF.ST : Warplink                @ 0x0612 (1554)
     *
     * LEV1     : Explosion 0             @ 0x0080 (128)
     * LEV1     : Explosion 1             @ 0x00BA (186)
     * LEV1     : Explosion 2             @ 0x012C (300)
     * LEV1     : Explosion 3             @ 0x019E (414)
     * LEV1     : Explosion 4             @ 0x0210 (528)
     * LEV1     : Explosion 5             @ 0x0282 (642)
     * LEV1     : Explosion 6             @ 0x02F4 (756)
     * LEV1     : Explosion 7             @ 0x0366 (870)
     * LEV1     : Impact 0                @ 0x03D8 (984)
     * LEV1     : Impact 1                @ 0x03FB (1019)
     * LEV1     : Impact 2                @ 0x042D (1069)
     * LEV1     : Impact 3                @ 0x045F (1119)
     * LEV1     : Impact 4                @ 0x0491 (1169)
     * LEV1     : Impact 5                @ 0x04C3 (1219)
     * LEV1     : Impact 6                @ 0x04F5 (1269)
     * LEV1     : Impact 7                @ 0x0527 (1319)
     * LEV1     : Sandsled                @ 0x0559 (1369)
     * LEV1     : Hovercraft              @ 0x05BA (1466)
     * LEV1     : Skimmer                 @ 0x0641 (1601)
     * LEV1     : Laser Projectile        @ 0x0694 (1684)
     * LEV1     : Cannon Projectile       @ 0x06C6 (1734)
     * LEV1     : Obstacle                @ 0x06E7 (1767)
     * LEV1     : Warplink                @ 0x0719 (1817)
     *
     * LEV7     : Explosion 0             @ 0x0080 (128)
     * LEV7     : Explosion 1             @ 0x00BA (186)
     * LEV7     : Explosion 2             @ 0x012C (300)
     * LEV7     : Explosion 3             @ 0x019E (414)
     * LEV7     : Explosion 4             @ 0x0210 (528)
     * LEV7     : Explosion 5             @ 0x0282 (642)
     * LEV7     : Explosion 6             @ 0x02F4 (756)
     * LEV7     : Explosion 7             @ 0x0366 (870)
     * LEV7     : Impact 0                @ 0x03D8 (984)
     * LEV7     : Impact 1                @ 0x03FB (1019)
     * LEV7     : Impact 2                @ 0x042D (1069)
     * LEV7     : Impact 3                @ 0x045F (1119)
     * LEV7     : Impact 4                @ 0x0491 (1169)
     * LEV7     : Impact 5                @ 0x04C3 (1219)
     * LEV7     : Impact 6                @ 0x04F5 (1269)
     * LEV7     : Impact 7                @ 0x0527 (1319)
     * LEV7     : Stalker                 @ 0x0559 (1369)
     * LEV7     : Gir Draxon              @ 0x05D5 (1493)
     * LEV7     : Pulsar                  @ 0x0684 (1668)
     * LEV7     : Stinger                 @ 0x06CE (1742)
     * LEV7     : Guiser                  @ 0x0705 (1797)
     * LEV7     : Laser Projectile        @ 0x0737 (1847)
     * LEV7     : Cannon Projectile       @ 0x0769 (1897)
     * LEV7     : Heavy Cannon Projectile @ 0x078A (1930)
     * LEV7     : Obstacle                @ 0x07AB (1963)
     * </pre>
     */
    static String meshEnumLine(byte[] rom, int offset, String name, int scale) {
        int p = offset;

        // vertices
        List<float[]> verts = new ArrayList<>();
        while ((rom[p] & 0xFF) != END) {
            int dist = rom[p++] & 0xFF;
            int angle = rom[p++] & 0xFF;
            int y = (byte) rom[p++];     // signed

            double rads = (angle / 128.0) * Math.PI;   // plugin math
            double x = cos(rads) * dist;
            double z = sin(rads) * dist;

            verts.add(new float[]{(float) x, (float) y, (float) z});
        }
        p++; // skip END

        // edges
        List<int[]> edges = new ArrayList<>();
        while ((rom[p] & 0xFF) != END) {
            int v0 = rom[p++] & 0xFF;
            int v1 = rom[p++] & 0xFF;
            edges.add(new int[]{v0, v1});
        }

        // emit "W1 V <n> x y z ... E <m> i j ... P 0"
        StringBuilder s = new StringBuilder();
        s.append("W1 V ").append(verts.size()).append(' ');
        for (float[] v : verts) {
            int xi = Math.round(v[0] * scale);
            int yi = Math.round(v[1] * scale);
            int zi = Math.round(v[2] * scale);
            s.append(xi).append(' ').append(yi).append(' ').append(zi).append(' ');
        }
        s.append("E ").append(edges.size()).append(' ');
        for (int[] e : edges) {
            s.append(e[0]).append(' ').append(e[1]).append(' ');
        }
        s.append("P 0");

        String enumName = name.toUpperCase().replace(' ', '_');
        return enumName + "(\"" + s.toString().trim() + "\"),";
    }

}
