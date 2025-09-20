package bzone;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute;
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder;
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class Models {

    public static final int[][] LIFE_ICON_STROKES = {
        {0, 0, -6, 6, 3, 9, 6, 15, 42, 6, 36, 0, 0, 0},
        {18, 12, 39, 12, 39, 9, 30, 9}
    };

    public enum Mesh {
        NARROW_PYRAMID,
        TALL_BOX,
        WIDE_PYRAMID,
        SHORT_BOX,
        SLOW_TANK,
        SUPER_TANK,
        SAUCER,
        PROJECTILE,
        ROCKET,
        MISSILE,
        RADAR,
        CHUNK_TANK,
        CHUNK0,
        CHUNK1,
        CHUNK2,
        CHUNK3,
        LOGO_BA,
        LOGO_TTLE,
        LOGO_ZONE,
        //stellar7
        GIR_DRAXON,
        WARPLINK,
        FUELBAY,
        SEEKER,
        STINGER,
        SKIMMER,
        PULSAR,
        GUN_BATTERY,
        LASER_BATTERY,
        STALKER,
        HEAVY_TANK,
        PROWLER,
        HOVERCRAFT,
        SAND_SLED,
        LASER_TANK,
        ABRAMS("assets/data/extra-objects.obj", 1f);

        private final String fname;
        private final float scale;

        Mesh() {
            this.fname = "assets/data/bzone-objects.obj";
            this.scale = 1f;
        }

        Mesh(String fname, float scale) {
            this.fname = fname;
            this.scale = scale;
        }

        public String fname() {
            return this.fname;
        }

        public float scale() {
            return this.scale;
        }
    }

    public static GameModelInstance getModelInstance(Mesh mesh, Color color, float unitScale) {
        try {
            Model model = loadModel(mesh.fname(), mesh.name(), color, mesh.scale());
            GameModelInstance instance = new GameModelInstance(mesh, model);
            return instance;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static GameModelInstance getModelInstance(Mesh mesh, Color color, float thicknessWorldUnits, boolean additiveGlow) {
        try {
            Model model = loadModelWithTubes(mesh.fname(), mesh.name(), color, mesh.scale(), thicknessWorldUnits, additiveGlow);
            GameModelInstance instance = new GameModelInstance(mesh, model);
            return instance;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private static final Vector3 TMP_MID = new Vector3();
    private static final Vector3 TMP_DIR = new Vector3();
    private static final Vector3 TMP_AXIS = new Vector3();
    private static final Quaternion TMP_Q = new Quaternion();
    private static final Matrix4 TMP_MAT = new Matrix4();

    public static List<ModelInstance> loadBackgroundObjects(String fname, float scale) {
        try {
            ObjData data = parseObj(fname);

            final Material greenMat = new Material(
                    ColorAttribute.createDiffuse(Color.GREEN),
                    ColorAttribute.createEmissive(Color.GREEN),
                    IntAttribute.createCullFace(GL20.GL_NONE)
            );

            final VertexAttributes edgeVA = new VertexAttributes(
                    new VertexAttribute(VertexAttributes.Usage.Position, 3, ShaderProgram.POSITION_ATTRIBUTE),
                    new VertexAttribute(VertexAttributes.Usage.ColorUnpacked, 4, ShaderProgram.COLOR_ATTRIBUTE)
            );

            List<ModelInstance> out = new ArrayList<>();

            for (ObjObject obj : data.objects) {
                ModelBuilder mb = new ModelBuilder();
                mb.begin();

                MeshPartBuilder edges = mb.part(obj.name + "_edges", GL20.GL_LINES, edgeVA, greenMat);

                final HashSet<Long> edgeSet = new HashSet<>();

                for (int[] chain : obj.lines) {
                    for (int i = 0; i < chain.length - 1; i++) {
                        addEdge(edgeSet, chain[i], chain[i + 1]);
                    }
                }

                for (long key : edgeSet) {
                    int i0 = (int) (key >>> 32), i1 = (int) key;
                    Vector3 p0 = data.vertices.get(i0), p1 = data.vertices.get(i1);
                    short s0 = edges.vertex(new VertexInfo().setPos(p0).setCol(Color.GREEN));
                    short s1 = edges.vertex(new VertexInfo().setPos(p1).setCol(Color.GREEN));
                    edges.line(s0, s1);
                }

                Model model = mb.end();
                ModelInstance inst = new ModelInstance(model);
                out.add(inst);
            }

            return out;
        } catch (IOException e) {
            throw new RuntimeException("Failed to read OBJ", e);
        }
    }

    public static Model loadModel(String fname, String name, Color color, float scale) throws Exception {

        ObjData data = parseObj(fname);

        final Material material = new Material(
                ColorAttribute.createDiffuse(color),
                ColorAttribute.createEmissive(color),
                IntAttribute.createCullFace(GL20.GL_NONE)
        );

        final VertexAttributes edgeVA = new VertexAttributes(
                new VertexAttribute(VertexAttributes.Usage.Position, 3, ShaderProgram.POSITION_ATTRIBUTE),
                new VertexAttribute(VertexAttributes.Usage.ColorUnpacked, 4, ShaderProgram.COLOR_ATTRIBUTE)
        );

        for (ObjObject obj : data.objects) {
            if (!obj.name.equals(name)) {
                continue;
            }

            float minY = Float.POSITIVE_INFINITY;
            for (int[] chain : obj.lines) {
                for (int vi : chain) {
                    float y = data.vertices.get(vi).y;
                    if (y < minY) {
                        minY = y;
                    }
                }
            }
            final float yOffset = (minY == Float.POSITIVE_INFINITY) ? 0f : -minY;

            ModelBuilder mb = new ModelBuilder();
            mb.begin();

            MeshPartBuilder edges = mb.part(obj.name + "_edges", GL20.GL_LINES, edgeVA, material);

            final HashSet<Long> edgeSet = new HashSet<>();
            for (int[] chain : obj.lines) {
                for (int i = 0; i < chain.length - 1; i++) {
                    addEdge(edgeSet, chain[i], chain[i + 1]);
                }
            }

            Vector3 tmp0 = new Vector3();
            Vector3 tmp1 = new Vector3();

            for (long key : edgeSet) {
                int i0 = (int) (key >>> 32), i1 = (int) key;
                Vector3 p0 = tmp0.set(data.vertices.get(i0)).add(0f, yOffset, 0f);
                Vector3 p1 = tmp1.set(data.vertices.get(i1)).add(0f, yOffset, 0f);
                short s0 = edges.vertex(new VertexInfo().setPos(p0).setCol(Color.GREEN));
                short s1 = edges.vertex(new VertexInfo().setPos(p1).setCol(Color.GREEN));
                edges.line(s0, s1);
            }

            Model model = mb.end();
            model.nodes.get(0).scale.set(scale, scale, scale);
            return model;
        }

        return null;
    }

    public static Model loadModelWithTubes(String fname, String name, Color color, float scale, float thicknessWorldUnits, boolean additiveGlow) throws Exception {

        ObjData data = parseObj(fname);

        final Material material = new Material(
                ColorAttribute.createDiffuse(color),
                ColorAttribute.createEmissive(color),
                IntAttribute.createCullFace(GL20.GL_NONE)
        );

        if (additiveGlow) {
            material.set(new BlendingAttribute(true, GL20.GL_SRC_ALPHA, GL20.GL_ONE, 1f));
        }

        final VertexAttributes tubeVA = new VertexAttributes(
                new VertexAttribute(VertexAttributes.Usage.Position, 3, ShaderProgram.POSITION_ATTRIBUTE),
                new VertexAttribute(VertexAttributes.Usage.Normal, 3, ShaderProgram.NORMAL_ATTRIBUTE),
                new VertexAttribute(VertexAttributes.Usage.ColorUnpacked, 4, ShaderProgram.COLOR_ATTRIBUTE)
        );

        for (ObjObject obj : data.objects) {
            if (!obj.name.equals(name)) {
                continue;
            }

            float minY = Float.POSITIVE_INFINITY;
            for (int[] chain : obj.lines) {
                for (int vi : chain) {
                    float y = data.vertices.get(vi).y;
                    if (y < minY) {
                        minY = y;
                    }
                }
            }
            final float yOffset = (minY == Float.POSITIVE_INFINITY) ? 0f : -minY;

            ModelBuilder mb = new ModelBuilder();
            mb.begin();

            MeshPartBuilder tubes = mb.part(obj.name + "_tubes", GL20.GL_TRIANGLES, tubeVA, material);

            final HashSet<Long> edgeSet = new HashSet<>();
            for (int[] chain : obj.lines) {
                for (int i = 0; i < chain.length - 1; i++) {
                    addEdge(edgeSet, chain[i], chain[i + 1]);
                }
            }

            // Build a tube per edge
            final Vector3 tmp0 = new Vector3();
            final Vector3 tmp1 = new Vector3();
            final float radius = thicknessWorldUnits * 0.5f;
            final int divisions = 12;

            tubes.setColor(color);

            for (long key : edgeSet) {
                int i0 = (int) (key >>> 32), i1 = (int) key;
                Vector3 p0 = tmp0.set(data.vertices.get(i0)).add(0f, yOffset, 0f);
                Vector3 p1 = tmp1.set(data.vertices.get(i1)).add(0f, yOffset, 0f);

                addTube(tubes, p0, p1, radius, divisions);
            }

            Model model = mb.end();

            if (!model.nodes.isEmpty()) {
                model.nodes.get(0).scale.set(scale, scale, scale);
            }
            return model;
        }

        return null;
    }

    private static ObjData parseObj(String fname) throws IOException {
        FileHandle fh = Gdx.files.classpath(fname);
        try (BufferedReader br = fh.reader(64 * 1024)) {
            ArrayList<Vector3> verts = new ArrayList<>();
            ArrayList<ObjObject> objects = new ArrayList<>();
            ObjObject current = null;

            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                if (line.startsWith("v ")) {
                    String[] tok = splitWS(line, 4);
                    float x = Float.parseFloat(tok[1]);
                    float y = Float.parseFloat(tok[2]);
                    float z = Float.parseFloat(tok[3]);
                    verts.add(new Vector3(x, y, z));
                } else if (line.startsWith("o ")) {
                    String name = line.substring(2).trim();
                    current = new ObjObject(name);
                    objects.add(current);

                } else if (line.startsWith("l ")) {
                    String[] tok = line.split("\\s+");
                    int n = tok.length - 1;
                    if (n >= 2) {
                        int[] poly = new int[n];
                        for (int i = 0; i < n; i++) {
                            poly[i] = parseIndex(tok[i + 1], verts.size());
                        }
                        current.lines.add(poly);
                    }
                }
            }

            return new ObjData(verts, objects);
        }
    }

    private static int parseIndex(String token, int vertCount) {
        int slash = token.indexOf('/');
        String viStr = (slash < 0) ? token : token.substring(0, slash);
        int vi = Integer.parseInt(viStr);
        if (vi > 0) {
            return vi - 1;
        } else {
            return vertCount + vi;
        }
    }

    private static void addEdge(HashSet<Long> set, int a, int b) {
        int i0 = Math.min(a, b);
        int i1 = Math.max(a, b);
        long key = ((long) i0 << 32) | (i1 & 0xFFFFFFFFL);
        set.add(key);
    }

    private static void addTube(MeshPartBuilder b, Vector3 a, Vector3 c, float radius, int divs) {
        // Build a cylinder centered on the segment, oriented along it.
        TMP_MID.set(a).add(c).scl(0.5f);
        TMP_DIR.set(c).sub(a);
        float len = TMP_DIR.len();
        if (len <= 0f) {
            return;
        }
        TMP_DIR.scl(1f / len);

        float dot = TMP_DIR.dot(Vector3.Y);
        if (dot > 0.9999f) {
            TMP_Q.idt();
        } else if (dot < -0.9999f) {
            TMP_Q.setFromAxis(Vector3.X, 180f);
        } else {
            TMP_AXIS.set(Vector3.Y).crs(TMP_DIR).nor();
            float deg = (float) Math.toDegrees(Math.acos(Math.min(1f, Math.max(-1f, dot))));
            TMP_Q.setFromAxis(TMP_AXIS, deg);
        }

        TMP_MAT.idt().translate(TMP_MID).rotate(TMP_Q).scale(radius * 2f, len, radius * 2f);
        b.setVertexTransform(TMP_MAT);
        b.cylinder(1f, 1f, 1f, divs); // unit cylinder → scaled/oriented by TMP_MAT
        b.setVertexTransform(null);
    }

    private static String[] splitWS(String s, int expected) {
        String[] out = new String[expected];
        int idx = 0;
        int i = 0, len = s.length();
        while (i < len && idx < expected) {
            while (i < len && Character.isWhitespace(s.charAt(i))) {
                i++;
            }
            int start = i;
            while (i < len && !Character.isWhitespace(s.charAt(i))) {
                i++;
            }
            if (start < i) {
                out[idx++] = s.substring(start, i);
            }
        }
        if (idx != expected) {
            return s.trim().split("\\s+"); // fallback
        }
        return out;

    }

    private static class ObjData {

        final ArrayList<Vector3> vertices;
        final ArrayList<ObjObject> objects;

        ObjData(ArrayList<Vector3> vertices, ArrayList<ObjObject> objects) {
            this.vertices = vertices;
            this.objects = objects;
        }
    }

    private static class ObjObject {

        final String name;
        final ArrayList<int[]> lines = new ArrayList<>();

        ObjObject(String name) {
            this.name = name;
        }
    }

    public static ModelInstance buildXZGrid(int halfLines, float spacing, Color color) {
        ModelBuilder mb = new ModelBuilder();
        mb.begin();
        Material mat = new Material(ColorAttribute.createDiffuse(color));
        MeshPartBuilder b = mb.part("xzGrid", GL20.GL_LINES,
                VertexAttributes.Usage.Position | VertexAttributes.Usage.ColorUnpacked, mat);
        b.setColor(color);

        float extent = halfLines * spacing;

        // Lines parallel to X (vary Z)
        for (int i = -halfLines; i <= halfLines; i++) {
            float z = i * spacing;
            b.line(-extent, 0f, z, +extent, 0f, z);
        }
        // Lines parallel to Z (vary X)
        for (int i = -halfLines; i <= halfLines; i++) {
            float x = i * spacing;
            b.line(x, 0f, -extent, x, 0f, +extent);
        }

        // Bold axes (X: red-ish, Z: blue-ish, Y: green)
        Color xAxis = new Color(0.9f, 0.2f, 0.2f, 1f);
        Color zAxis = new Color(0.2f, 0.4f, 0.9f, 1f);
        Color yAxis = new Color(0.2f, 0.9f, 0.2f, 1f);

        b.setColor(xAxis);
        b.line(-extent, 0f, 0f, +extent, 0f, 0f);

        b.setColor(zAxis);
        b.line(0f, 0f, -extent, 0f, 0f, +extent);

        // Add Y axis line upward from origin
        b.setColor(yAxis);
        b.line(0f, 0f, 0f, 0f, extent, 0f);

        Model gridModel = mb.end();
        return new ModelInstance(gridModel, new Matrix4().idt());
    }

    public static final float[][] DEATH_CRACK_LINES = {
        {640, 480, 731, 472},
        {731, 472, 813, 544},
        {813, 544, 897, 578},
        {897, 578, 961, 698},
        {961, 698, 1554, 1027},
        {961, 698, 997, 829},
        {997, 829, 1069, 945},
        {897, 578, 987, 585},
        {987, 585, 1076, 602},
        {1076, 602, 1166, 589},
        {813, 544, 888, 625},
        {888, 625, 982, 681},
        {731, 472, 817, 443},
        {817, 443, 889, 385},
        {889, 385, 867, 290},
        {867, 290, 858, 194},
        {858, 194, 1081, -101},
        {858, 194, 818, 106},
        {889, 385, 975, 348},
        {975, 348, 1067, 342},
        {817, 443, 903, 411},
        {903, 411, 1032, 384},
        {1032, 384, 1337, -101},
        {1032, 384, 1127, 419},
        {1127, 419, 1222, 452},
        {1222, 452, 1563, 184},
        {1222, 452, 1218, 537},
        {1218, 537, 1235, 619},
        {1235, 619, 1413, 829},
        {1235, 619, 1229, 704},
        {1229, 704, 1203, 784},
        {1222, 452, 1318, 483},
        {1318, 483, 1418, 492},
        {1418, 492, 1508, 473},
        {1508, 473, 1590, 434},
        {1590, 434, 1678, 410},
        {1678, 410, 1765, 382},
        {1032, 384, 1162, 363},
        {1162, 363, 1284, 313},
        {1284, 313, 1406, 306},
        {1406, 306, 1526, 328},
        {1526, 328, 1549, 460},
        {1549, 460, 1266, 1048},
        {1549, 460, 1585, 590},
        {1585, 590, 1624, 718},
        {1526, 328, 1647, 340},
        {1647, 340, 1781, 290},
        {1781, 290, 1901, 210},
        {1901, 210, 2001, 106},
        {1647, 340, 1767, 361},
        {1767, 361, 2161, -3},
        {903, 411, 994, 407},
        {640, 480, 693, 528},
        {693, 528, 709, 630},
        {709, 630, 754, 723},
        {754, 723, 551, 1173},
        {754, 723, 787, 820},
        {693, 528, 748, 573},
        {748, 573, 1031, 604},
        {748, 573, 781, 642},
        {781, 642, 801, 716},
        {801, 716, 835, 784},
        {748, 573, 815, 598},
        {640, 480, 576, 552},
        {576, 552, 519, 629},
        {519, 629, 484, 718},
        {484, 718, 456, 809},
        {640, 480, 580, 442},
        {580, 442, 520, 403},
        {520, 403, 246, 458},
        {520, 403, 442, 401},
        {442, 401, 389, 470},
        {389, 470, 494, 836},
        {389, 470, 350, 547},
        {350, 547, 302, 620},
        {302, 620, 233, 672},
        {442, 401, 369, 375},
        {369, 375, 302, 419},
        {302, 419, 242, 473},
        {242, 473, 238, 872},
        {242, 473, 173, 515},
        {173, 515, 108, 562},
        {369, 375, 298, 344},
        {298, 344, 234, 299},
        {234, 299, 156, 251},
        {156, 251, 61, 229},
        {61, 229, -112, -193},
        {61, 229, -27, 185},
        {-27, 185, -116, 145},
        {-116, 145, -240, -133},
        {-116, 145, -211, 120},
        {156, 251, 75, 209},
        {75, 209, -5, 163},
        {-5, 163, -81, 111},
        {520, 403, 451, 384},
        {451, 384, 385, 356},
        {640, 480, 686, 420},
        {686, 420, 718, 351},
        {718, 351, 692, 268},
        {692, 268, 654, 190},
        {654, 190, 265, 122},
        {654, 190, 644, 104},
        {718, 351, 735, 277},
        {735, 277, 762, 206},
        {762, 206, 641, -103},};

    public static void drawDeathCracks(ShapeRenderer sr) {
        Gdx.gl.glLineWidth(3);
        sr.begin(ShapeRenderer.ShapeType.Line);
        sr.setColor(Color.GREEN);

        for (float[] vert : DEATH_CRACK_LINES) {
            sr.line(vert[0], vert[1], vert[2], vert[3]);
        }

        sr.end();
        Gdx.gl.glLineWidth(1);
    }

}
