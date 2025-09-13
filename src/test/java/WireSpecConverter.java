
import bzone.Models;
import bzone.Models.Mesh;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * WireSpecConverter ------------------ A tiny, dependency-free Java CLI that
 * parses your Battlezone-style wire model string (e.g., "W1 V <n> <x y z ...> E
 * <pairs...> [P <pivot>]"), then writes: - .g3dj (LibGDX JSON) as a LINES mesh
 * (good for LibGDX; convert to .g3db with UBJson if desired) - .obj (Wavefront)
 * using poly-lines (works in Blender out-of-the-box for visualization)
 *
 * NOTE about Blender: Blender does not natively import LibGDX .g3dj/.g3db. If
 * your goal is Blender, export .obj with --format obj (default if output ends
 * with .obj).
 *
 * Usage examples: java WireSpecConverter \ --name SLOW_TANK \ --string "W1 V 25
 * 512 -640 -736 ... 0 P 0" \ --out slow_tank.g3dj
 *
 * java WireSpecConverter --in slow_tank.txt --out slow_tank.obj --scale 0.01
 * --zup
 *
 * Options: --name <id> Node/mesh id to embed (defaults to "wire") --string
 * <spec> Inline model spec. (Mutually exclusive with --in) --in <file> Read
 * model spec from a text file. (Mutually exclusive with --string) --out <file>
 * Output file path; extension controls default format (g3dj|obj) --format <fmt>
 * Explicit format: g3dj | obj --scale <s> Uniform scale factor (default 1.0)
 * --zup Swap Y/Z to make Z the up-axis (useful for Blender)
 *
 * Supported source tokens (order-insensitive after the leading "W* V"): W*
 * Ignored (wire version/flag) V <n> Number of vertices followed by 3*n floats
 * (xyz) E [<m>] ... Edge indices as pairs i0 j0 i1 j1 ... Optionally prefixed
 * by count m P <idx> Optional pivot index (ignored; reserved for future use)
 */
public class WireSpecConverter {

    public static void main(String[] args) throws Exception {
        //Cli cli = Cli.parse(args);
        Mesh m = Models.Mesh.SLOW_TANK;
        Cli cli = new Cli(m.name(), m.text(), null, m.name() + ".obj", "obj", 1, false);
        if (cli == null) {
            Cli.printHelpAndExit(null);
        }

        String raw = cli.inlineSpec != null ? cli.inlineSpec : Files.readString(Path.of(cli.inputPath), StandardCharsets.UTF_8);
        Model model = WireSpecParser.parse(cli.name, raw);

        // transforms
        if (cli.scale != 1.0) {
            model.scale((float) cli.scale);
        }
        if (cli.zup) {
            model.swapYandZ();
        }

        String out = cli.outputPath;
        String fmt = cli.format != null ? cli.format.toLowerCase(Locale.ROOT) : inferFormat(out);
        switch (fmt) {
            case "g3dj" ->
                writeG3DJ(model, out);
            case "obj" ->
                writeOBJ(model, out);
            default ->
                Cli.printHelpAndExit("Unsupported --format: " + fmt);
        }
        System.out.println("Wrote " + fmt + " → " + out + " (verts=" + model.vertices.size() / 3 + ", segments=" + model.edges.size() / 2 + ")");
    }

    private static String inferFormat(String out) {
        String lo = out.toLowerCase(Locale.ROOT);
        if (lo.endsWith(".g3dj")) {
            return "g3dj";
        }
        if (lo.endsWith(".obj")) {
            return "obj";
        }
        return "g3dj"; // default
    }

    // ---------- Writers ----------
    private static void writeG3DJ(Model m, String path) throws IOException {
        // Minimal g3dj with a single mesh (POSITION only) and one LINES mesh part.
        // Node references the mesh-part by id, and a dummy material is provided.
        String meshId = m.name + "_mesh";
        String partId = m.name + "_lines";
        String matId = "wire";

        try (BufferedWriter w = Files.newBufferedWriter(Path.of(path), StandardCharsets.UTF_8)) {
            JsonOut json = new JsonOut(w);
            json.objBegin();
            json.key("version").arrayBegin().num(0).sep().num(1).arrayEnd();
            json.sep();
            json.key("id").str(m.name);
            json.sep();

            // meshes
            json.key("meshes").arrayBegin();
            json.objBegin();
            json.key("id").str(meshId);
            json.sep();
            json.key("attributes").arrayBegin().str("POSITION").arrayEnd();
            json.sep();
            json.key("vertices").arrayBegin();
            for (int i = 0; i < m.vertices.size(); i++) {
                if (i > 0) {
                    json.sep();
                }
                json.num(m.vertices.get(i));
            }
            json.arrayEnd();
            json.sep();
            json.key("parts").arrayBegin();
            json.objBegin();
            json.key("id").str(partId);
            json.sep();
            json.key("type").str("LINES");
            json.sep();
            json.key("indices").arrayBegin();
            for (int i = 0; i < m.edges.size(); i++) {
                if (i > 0) {
                    json.sep();
                }
                json.num(m.edges.get(i));
            }
            json.arrayEnd();
            json.objEnd();
            json.arrayEnd();
            json.objEnd();
            json.arrayEnd();
            json.sep();

            // materials (simple white diffuse)
            json.key("materials").arrayBegin();
            json.objBegin();
            json.key("id").str(matId);
            json.sep();
            json.key("diffuse").arrayBegin().num(1).sep().num(1).sep().num(1).arrayEnd();
            json.objEnd();
            json.arrayEnd();
            json.sep();

            // nodes referencing the meshpart
            json.key("nodes").arrayBegin();
            json.objBegin();
            json.key("id").str(m.name);
            json.sep();
            json.key("parts").arrayBegin();
            json.objBegin();
            json.key("meshpartid").str(partId);
            json.sep();
            json.key("materialid").str(matId);
            json.objEnd();
            json.arrayEnd();
            json.objEnd();
            json.arrayEnd();
            json.sep();

            json.key("animations").arrayBegin().arrayEnd();
            json.objEnd();
        }
    }

    private static void writeOBJ(Model m, String path) throws IOException {
        try (BufferedWriter w = Files.newBufferedWriter(Path.of(path), StandardCharsets.UTF_8)) {
            w.write("# OBJ wire export generated by WireSpecConverter\n");
            w.write("o " + m.name + "\n");
            for (int i = 0; i < m.vertices.size(); i += 3) {
                float x = m.vertices.get(i);
                float y = m.vertices.get(i + 1);
                float z = m.vertices.get(i + 2);
                w.write(String.format(Locale.ROOT, "v %.6f %.6f %.6f\n", x, y, z));
            }
            // Write each edge as an OBJ polyline (l i j). OBJ is 1-based.
            for (int i = 0; i < m.edges.size(); i += 2) {
                int a = m.edges.get(i) + 1;
                int b = m.edges.get(i + 1) + 1;
                w.write("l " + a + " " + b + "\n");
            }
        }
    }

    // ---------- Parsing ----------
    static final class WireSpecParser {

        static Model parse(String name, String raw) {
            String inner = extractInner(raw);
            List<String> tokens = tokenize(inner);
            List<Float> verts = new ArrayList<>();
            List<Integer> edges = new ArrayList<>();

            int i = 0;
            int vCount = -1;
            while (i < tokens.size()) {
                String t = tokens.get(i++);
                switch (t) {
                    case "W", "W0", "W1", "W2" -> {
                        /* ignore */ }
                    case "V" -> {
                        if (i >= tokens.size()) {
                            fail("Expected vertex count after V");
                        }
                        vCount = toInt(tokens.get(i++));
                        int need = 3 * vCount;
                        if (i + need > tokens.size()) {
                            fail("Not enough numbers for " + vCount + " vertices");
                        }
                        for (int k = 0; k < need; k++) {
                            verts.add(toFloat(tokens.get(i++)));
                        }
                    }
                    case "E" -> {
                        // collect until next alpha token or end
                        int start = i;
                        while (i < tokens.size() && isNumber(tokens.get(i))) {
                            i++;
                        }
                        List<Integer> idx = new ArrayList<>();
                        for (int k = start; k < i; k++) {
                            idx.add(toInt(tokens.get(k)));
                        }
                        if (idx.isEmpty()) {
                            fail("E with no indices");
                        }
                        // Optional leading count: if first*2 == (n-1), strip it
                        if (idx.size() > 1 && idx.get(0) * 2 == (idx.size() - 1)) {
                            idx.remove(0);
                        }
                        if (idx.size() % 2 != 0) {
                            fail("Edge index list must contain pairs, got " + idx.size());
                        }
                        edges.addAll(idx);
                    }
                    case "P" -> { // pivot index, ignore but consume one int if present
                        if (i < tokens.size() && isNumber(tokens.get(i))) {
                            i++;
                        }
                    }
                    default -> {
                        /* tolerate stray tokens */ }
                }
            }
            if (vCount < 0) {
                fail("Missing V section");
            }
            // basic validation
            for (int k = 0; k < edges.size(); k++) {
                int idx = edges.get(k);
                if (idx < 0 || idx >= vCount) {
                    fail("Edge index out of range: " + idx + " (vcount=" + vCount + ")");
                }
            }
            return new Model(name != null ? name : "wire", verts, edges);
        }

        private static String extractInner(String raw) {
            raw = raw.trim();
            // Accept forms: NAME("...")  or  "..."  or bare text
            int q1 = raw.indexOf('"');
            int q2 = raw.lastIndexOf('"');
            if (q1 >= 0 && q2 > q1) {
                return raw.substring(q1 + 1, q2);
            }
            // remove leading NAME( and trailing ) if present
            int p1 = raw.indexOf('(');
            int p2 = raw.lastIndexOf(')');
            if (p1 >= 0 && p2 > p1) {
                return raw.substring(p1 + 1, p2);
            }
            return raw;
        }

        private static List<String> tokenize(String s) {
            List<String> out = new ArrayList<>();
            StringBuilder cur = new StringBuilder();
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (Character.isWhitespace(c)) {
                    flush(cur, out);
                } else if (c == ',' || c == ';') {
                    flush(cur, out);
                } else if (c == 'V' || c == 'E' || c == 'P') { // markers as separate tokens
                    flush(cur, out);
                    out.add(String.valueOf(c));
                } else if (c == 'W') {
                    flush(cur, out);
                    // capture W, W1, W2...
                    StringBuilder w = new StringBuilder();
                    w.append('W');
                    int j = i + 1;
                    while (j < s.length() && Character.isDigit(s.charAt(j))) {
                        w.append(s.charAt(j++));
                    }
                    out.add(w.toString());
                    i = j - 1;
                } else {
                    cur.append(c);
                }
            }
            flush(cur, out);
            // trim empties
            out.removeIf(String::isEmpty);
            return out;
        }

        private static void flush(StringBuilder b, List<String> out) {
            if (b.length() > 0) {
                out.add(b.toString());
                b.setLength(0);
            }
        }

        private static boolean isNumber(String s) {
            if (s == null || s.isEmpty()) {
                return false;
            }
            char c = s.charAt(0);
            return Character.isDigit(c) || c == '-' || c == '+' || c == '.';
        }

        private static int toInt(String s) {
            try {
                return Integer.parseInt(s);
            } catch (Exception e) {
                fail("Bad int: " + s);
                return 0;
            }
        }

        private static float toFloat(String s) {
            try {
                return Float.parseFloat(s);
            } catch (Exception e) {
                fail("Bad float: " + s);
                return 0f;
            }
        }

        private static void fail(String msg) {
            throw new IllegalArgumentException(msg);
        }
    }

    // ---------- Data ----------
    static final class Model {

        final String name;
        final List<Float> vertices; // xyz xyz ...
        final List<Integer> edges;  // pairs of indices

        Model(String name, List<Float> v, List<Integer> e) {
            this.name = name;
            this.vertices = v;
            this.edges = e;
        }

        void scale(float s) {
            for (int i = 0; i < vertices.size(); i++) {
                vertices.set(i, vertices.get(i) * s);
            }
        }

        void swapYandZ() {
            for (int i = 0; i < vertices.size(); i += 3) {
                float y = vertices.get(i + 1);
                float z = vertices.get(i + 2);
                vertices.set(i + 1, z);
                vertices.set(i + 2, y);
            }
        }
    }

    // ---------- Minimal JSON writer ----------
    static final class JsonOut {

        private final Writer w;
        private final Deque<Character> ctx = new ArrayDeque<>();
        private boolean needComma = false;

        JsonOut(Writer w) {
            this.w = w;
        }

        JsonOut key(String k) throws IOException {
            commaIfNeeded();
            w.write('"');
            esc(k);
            w.write('"');
            w.write(':');
            needComma = false;
            return this;
        }

        JsonOut objBegin() throws IOException {
            commaIfNeeded();
            w.write('{');
            ctx.push('}');
            needComma = false;
            return this;
        }

        JsonOut objEnd() throws IOException {
            w.write(ctx.pop());
            needComma = true;
            return this;
        }

        JsonOut arrayBegin() throws IOException {
            commaIfNeeded();
            w.write('[');
            ctx.push(']');
            needComma = false;
            return this;
        }

        JsonOut arrayEnd() throws IOException {
            w.write(ctx.pop());
            needComma = true;
            return this;
        }

        JsonOut str(String s) throws IOException {
            commaIfNeeded();
            w.write('"');
            esc(s);
            w.write('"');
            needComma = true;
            return this;
        }

        JsonOut num(double d) throws IOException {
            commaIfNeeded();
            w.write(trimNum(d));
            needComma = true;
            return this;
        }

        JsonOut sep() throws IOException {
            w.write(',');
            needComma = false;
            return this;
        }

        private void commaIfNeeded() throws IOException {
            if (needComma) {
                w.write(',');
                needComma = false;
            }
        }

        private void esc(String s) throws IOException {
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                switch (c) {
                    case '"' -> {
                        w.write("\\\"");
                    }
                    case '\\' -> {
                        w.write("\\\\");
                    }
                    case '\n' -> {
                        w.write("\\n");
                    }
                    case '\r' -> {
                        w.write("\\r");
                    }
                    case '\t' -> {
                        w.write("\\t");
                    }
                    default -> {
                        if (c < 0x20) {
                            w.write(String.format(Locale.ROOT, "\\u%04x", (int) c));
                        } else {
                            w.write(c);
                        }
                    }
                }
            }
        }

        private static String trimNum(double d) {
            String s = String.format(Locale.ROOT, "%.6f", d);
            int i = s.indexOf('.');
            if (i >= 0) {
                int j = s.length() - 1;
                while (j > i && s.charAt(j) == '0') {
                    j--;
                }
                if (j == i) {
                    j--; // remove decimal point
                }
                s = s.substring(0, j + 1);
            }
            return s;
        }
    }

    // ---------- CLI ----------
    static final class Cli {

        final String name;
        final String inlineSpec;
        final String inputPath;
        final String outputPath;
        final String format; // g3dj|obj or null
        final double scale;
        final boolean zup;

        private Cli(String name, String inlineSpec, String inputPath, String outputPath, String format, double scale, boolean zup) {
            this.name = name;
            this.inlineSpec = inlineSpec;
            this.inputPath = inputPath;
            this.outputPath = outputPath;
            this.format = format;
            this.scale = scale;
            this.zup = zup;
        }

        static Cli parse(String[] args) {
            String name = "wire";
            String spec = null;
            String in = null;
            String out = null;
            String fmt = null;
            double scale = 1.0;
            boolean zup = false;

            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "--name" ->
                        name = args[++i];
                    case "--string" ->
                        spec = args[++i];
                    case "--in" ->
                        in = args[++i];
                    case "--out" ->
                        out = args[++i];
                    case "--format" ->
                        fmt = args[++i];
                    case "--scale" ->
                        scale = Double.parseDouble(args[++i]);
                    case "--zup" ->
                        zup = true;
                    case "-h", "--help" ->
                        printHelpAndExit(null);
                    default ->
                        printHelpAndExit("Unknown option: " + args[i]);
                }
            }
            if ((spec == null) == (in == null)) {
                printHelpAndExit("Provide exactly one of --string or --in");
            }
            if (out == null) {
                printHelpAndExit("--out <file> is required");
            }
            return new Cli(name, spec, in, out, fmt, scale, zup);
        }

        static void printHelpAndExit(String err) {
            if (err != null) {
                System.err.println("Error: " + err + "\n");
            }
            System.err.println("WireSpecConverter — convert Battlezone-style wire specs to G3DJ or OBJ\n"
                    + "Usage: java WireSpecConverter --name <id> (--string <spec> | --in <file>) --out <file> [--format g3dj|obj] [--scale s] [--zup]\n");
            System.exit(err == null ? 0 : 1);
        }
    }
}
