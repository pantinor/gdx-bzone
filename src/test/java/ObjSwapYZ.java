
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * ObjSwapYZ
 *
 * Reads an OBJ file and writes a new OBJ with Y and Z swapped for vertex
 * positions ("v" lines). Optionally also swaps Y and Z for vertex normals ("vn"
 * lines).
 *
 * Usage: java ObjSwapYZ <input.obj> <output.obj> [--swap-normals]
 *
 * Notes: - Only "v" (and optionally "vn") lines are modified. - Texture
 * coordinates "vt" are untouched. - Faces/winding are not changed (we are not
 * mirroring axes; just reorienting). - Supports both 3-component and
 * 4-component vertex positions (x y z [w]).
 */
public class ObjSwapYZ {

    public static void main(String[] args) {

        Path input = Path.of("src/main/resources/assets/data/background2.obj");
        Path output = Path.of("src/main/resources/assets/data/swapped-background2.obj");
        boolean swapNormals = args.length == 3 && "--swap-normals".equalsIgnoreCase(args[2]);

        try {
            process(input, output, swapNormals);
            System.out.println("Wrote: " + output.toAbsolutePath());
        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(2);
        }
    }

    /**
     * Process the OBJ: swap Y and Z on "v" lines; optionally on "vn" lines.
     */
    public static void process(Path input, Path output, boolean swapNormals) throws IOException {
        try (BufferedReader br = Files.newBufferedReader(input, StandardCharsets.UTF_8); BufferedWriter bw = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {

            String line;
            while ((line = br.readLine()) != null) {
                String trimmed = ltrim(line); // keep trailing spaces if any
                if (trimmed.startsWith("v ") || (swapNormals && trimmed.startsWith("vn "))) {
                    // Tokenize on whitespace, but we don't need to preserve exact spacing.
                    // Reconstruct with single spaces for numeric tokens.
                    String[] parts = trimmed.split("\\s+");
                    // parts[0] is "v" or "vn"
                    if (parts.length >= 4) {
                        // Expecting: cmd x y z [w]
                        String cmd = parts[0];
                        double x = parse(parts[1]);
                        double y = parse(parts[2]);
                        double z = parse(parts[3]);

                        // swap Y and Z
                        double newY = z;
                        double newZ = y;

                        if (parts.length >= 5) {
                            // homogeneous coordinate present
                            double w = parse(parts[4]);
                            bw.write(cmd + " " + fmt(x) + " " + fmt(newY) + " " + fmt(newZ) + " " + fmt(w));
                        } else {
                            bw.write(cmd + " " + fmt(x) + " " + fmt(newY) + " " + fmt(newZ));
                        }
                        bw.newLine();
                        continue;
                    }
                    // If malformed, just pass through unchanged.
                }
                // Default: write original line verbatim
                bw.write(line);
                bw.newLine();
            }
        }
    }

    private static double parse(String s) {
        // Handles scientific notation and negatives; throws NumberFormatException if bad.
        return Double.parseDouble(s);
    }

    private static String fmt(double d) {
        // Reasonable compact formatting: remove trailing zeros but keep precision.
        // Using %s via Double.toString keeps scientific notation when needed.
        String s = Double.toString(d);
        // Optionally trim trailing zeros on plain decimals for readability
        if (s.indexOf('E') >= 0 || s.indexOf('e') >= 0) {
            return s;
        }
        if (s.indexOf('.') >= 0) {
            // strip trailing zeros and possible trailing dot
            s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
        }
        return s;
    }

    private static String ltrim(String s) {
        int i = 0;
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
            i++;
        }
        return s.substring(i);
    }
}
