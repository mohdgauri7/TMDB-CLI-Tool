import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A minimal, dependency-free JSON parser.
 *
 * Parsing returns a tree of {@link Map} (objects), {@link List} (arrays),
 * {@link String}, {@link BigDecimal} (numbers), {@link Boolean}, and {@code null}.
 * Only parsing is needed here (we read TMDB's responses, we never produce JSON).
 */
final class Json {

    /** Guards against unbounded recursion on pathologically nested input. */
    private static final int MAX_DEPTH = 512;

    private final String src;
    private int pos;
    private int depth;

    private Json(String src) {
        this.src = src;
    }

    /** Parses JSON text into Map / List / String / BigDecimal / Boolean / null. */
    static Object parse(String text) {
        Json p = new Json(text);
        p.skipWs();
        Object value = p.readValue();
        p.skipWs();
        if (p.pos != p.src.length()) {
            throw new IllegalArgumentException("Unexpected trailing characters at index " + p.pos);
        }
        return value;
    }

    private Object readValue() {
        char c = peek();
        // Only the recursive container types need depth tracking; scalars don't recurse.
        if (c != '{' && c != '[') {
            return switch (c) {
                case '"' -> readString();
                case 't', 'f' -> readBoolean();
                case 'n' -> readNull();
                default -> readNumber();
            };
        }
        if (++depth > MAX_DEPTH) {
            throw err("Maximum nesting depth of " + MAX_DEPTH + " exceeded");
        }
        try {
            return (c == '{') ? readObject() : readArray();
        } finally {
            depth--;
        }
    }

    private Map<String, Object> readObject() {
        Map<String, Object> map = new LinkedHashMap<>();
        expect('{');
        skipWs();
        if (peek() == '}') {
            pos++;
            return map;
        }
        while (true) {
            skipWs();
            if (peek() != '"') {
                throw err("Expected a string key");
            }
            String key = readString();
            skipWs();
            expect(':');
            skipWs();
            map.put(key, readValue());
            skipWs();
            char c = next();
            if (c == '}') {
                return map;
            }
            if (c != ',') {
                throw err("Expected ',' or '}' in object");
            }
        }
    }

    private List<Object> readArray() {
        List<Object> list = new ArrayList<>();
        expect('[');
        skipWs();
        if (peek() == ']') {
            pos++;
            return list;
        }
        while (true) {
            skipWs();
            list.add(readValue());
            skipWs();
            char c = next();
            if (c == ']') {
                return list;
            }
            if (c != ',') {
                throw err("Expected ',' or ']' in array");
            }
        }
    }

    private String readString() {
        expect('"');
        StringBuilder sb = new StringBuilder();
        while (true) {
            if (pos >= src.length()) {
                throw err("Unterminated string");
            }
            char c = src.charAt(pos++);
            if (c == '"') {
                return sb.toString();
            }
            if (c == '\\') {
                char e = next();
                switch (e) {
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'u' -> {
                        if (pos + 4 > src.length()) {
                            throw err("Invalid \\u escape");
                        }
                        String hex = src.substring(pos, pos + 4);
                        pos += 4;
                        try {
                            sb.append((char) Integer.parseInt(hex, 16));
                        } catch (NumberFormatException nfe) {
                            throw err("Invalid \\u escape '" + hex + "'");
                        }
                    }
                    default -> throw err("Invalid escape '\\" + e + "'");
                }
            } else {
                sb.append(c);
            }
        }
    }

    private BigDecimal readNumber() {
        int start = pos;
        if (peek() == '-') {
            pos++;
        }
        while (pos < src.length() && isNumberChar(src.charAt(pos))) {
            pos++;
        }
        String token = src.substring(start, pos);
        try {
            return new BigDecimal(token);
        } catch (NumberFormatException e) {
            throw err("Invalid number '" + token + "'");
        }
    }

    private static boolean isNumberChar(char c) {
        return (c >= '0' && c <= '9') || c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-';
    }

    private Boolean readBoolean() {
        if (src.startsWith("true", pos)) {
            pos += 4;
            return Boolean.TRUE;
        }
        if (src.startsWith("false", pos)) {
            pos += 5;
            return Boolean.FALSE;
        }
        throw err("Invalid literal");
    }

    private Object readNull() {
        if (src.startsWith("null", pos)) {
            pos += 4;
            return null;
        }
        throw err("Invalid literal");
    }

    private void skipWs() {
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                pos++;
            } else {
                break;
            }
        }
    }

    private char peek() {
        if (pos >= src.length()) {
            throw err("Unexpected end of input");
        }
        return src.charAt(pos);
    }

    private char next() {
        if (pos >= src.length()) {
            throw err("Unexpected end of input");
        }
        return src.charAt(pos++);
    }

    private void expect(char c) {
        char actual = next();
        if (actual != c) {
            throw err("Expected '" + c + "' but found '" + actual + "'");
        }
    }

    private IllegalArgumentException err(String message) {
        return new IllegalArgumentException(message + " (at index " + pos + ")");
    }
}
