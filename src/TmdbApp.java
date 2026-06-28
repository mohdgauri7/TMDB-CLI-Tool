import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Command-line tool that fetches movie listings from TMDB and prints them.
 *
 * Usage:
 *   tmdb-app --type <playing|popular|top|upcoming> [--limit N] [--page N]
 *            [--api-key KEY | --token TOKEN]
 *
 * Credentials come from --api-key / --token, or the TMDB_API_KEY /
 * TMDB_API_TOKEN environment variables.
 */
public class TmdbApp {

    private static final Set<String> KNOWN_OPTIONS =
            Set.of("type", "limit", "page", "api-key", "api_key", "token", "help");
    private static final String DEFAULT_TYPE = "popular";
    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 20;   // TMDB returns 20 results per page
    private static final int MAX_PAGE = 500;   // TMDB caps pagination at 500
    private static final int OVERVIEW_WIDTH = 100;

    // ANSI colours, auto-disabled when output is piped or NO_COLOR is set.
    private static final boolean COLOR =
            System.console() != null && System.getenv("NO_COLOR") == null;

    public static void main(String[] args) {
        try {
            run(args);
        } catch (CliError e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        } catch (Exception e) {
            System.err.println("Unexpected error: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void run(String[] args) {
        Map<String, String> flags = parseFlags(args);
        if (flags.containsKey("help")) {
            printUsage();
            return;
        }
        for (Map.Entry<String, String> e : flags.entrySet()) {
            String key = e.getKey();
            if (!KNOWN_OPTIONS.contains(key)) {
                throw new CliError("Unknown option '--" + key + "'. Run 'tmdb-app --help' for usage.");
            }
            if (e.getValue().isEmpty()) { // --help is handled above and never reaches here
                throw new CliError("Option '--" + key + "' requires a value.");
            }
        }

        MovieType type = MovieType.fromArg(flags.getOrDefault("type", DEFAULT_TYPE));
        int limit = parseRange(flags.getOrDefault("limit", String.valueOf(DEFAULT_LIMIT)), "limit", 1, MAX_LIMIT);
        int page = parseRange(flags.getOrDefault("page", "1"), "page", 1, MAX_PAGE);
        String[] credentials = resolveCredentials(flags); // [apiKey, bearerToken]

        List<Movie> movies = new TmdbClient(credentials[0], credentials[1]).fetch(type, page);
        printMovies(movies, type, limit);
    }

    // ---- Output ----

    /** Prints up to {@code limit} movies. Package-private so it can be exercised in tests. */
    static void printMovies(List<Movie> movies, MovieType type, int limit) {
        if (movies.isEmpty()) {
            System.out.println("No " + type.label.toLowerCase(Locale.ROOT) + " movies found.");
            return;
        }
        int show = Math.min(limit, movies.size());
        System.out.println(bold(type.label + " movies:"));
        System.out.println();

        int width = String.valueOf(show).length();
        String indent = " ".repeat(width + 2);
        for (int i = 0; i < show; i++) {
            Movie m = movies.get(i);
            String title = (m.title() != null && !m.title().isBlank()) ? m.title() : "(untitled)";
            String year = year(m.releaseDate());
            String titleLine = title + (year != null ? " (" + year + ")" : "");
            String rating = yellow(String.format(Locale.US, "★ %.1f", m.rating()))
                    + dim(" (" + String.format(Locale.US, "%,d", m.voteCount()) + " votes)");
            String lang = (m.language() != null && !m.language().isBlank())
                    ? dim("  ·  " + m.language().toUpperCase(Locale.US))
                    : "";

            System.out.println(pad(String.valueOf(i + 1), width) + ". " + cyan(titleLine) + "   " + rating + lang);
            String overview = (m.overview() == null || m.overview().isBlank())
                    ? "(no overview)"
                    : truncate(m.overview(), OVERVIEW_WIDTH);
            System.out.println(indent + overview);
            System.out.println();
        }
    }

    private static void printUsage() {
        System.out.println("""
                tmdb-app - show movie listings from The Movie Database (TMDB).

                Usage:
                  tmdb-app --type <playing|popular|top|upcoming> [options]

                Options:
                  --type <type>      Listing to show: playing, popular, top, upcoming (default: popular)
                  --limit <N>        Number of movies to show, 1-20 (default: 10)
                  --page <N>         Result page, 1-500 (default: 1)
                  --api-key <key>    TMDB v3 API key (or set TMDB_API_KEY)
                  --token <token>    TMDB v4 read access token (or set TMDB_API_TOKEN)
                  --help             Show this help

                Examples:
                  tmdb-app --type popular
                  tmdb-app --type playing --limit 5
                  tmdb-app --type top --page 2

                Get a free API key at https://www.themoviedb.org/settings/api""");
    }

    // ---- Argument parsing & validation ----

    private static Map<String, String> parseFlags(String[] args) {
        Map<String, String> flags = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if (a.equals("-h") || a.equals("--help")) {
                flags.put("help", "");
                continue;
            }
            if (!a.startsWith("--")) {
                throw new CliError("Unexpected argument '" + a + "'. Options must look like --name value.");
            }
            String key = a.substring(2);
            String value;
            int eq = key.indexOf('=');
            if (eq >= 0) {
                value = key.substring(eq + 1);
                key = key.substring(0, eq);
            } else if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                value = args[++i];
            } else {
                value = "";
            }
            if (!key.isEmpty()) {
                flags.put(key.toLowerCase(Locale.ROOT), value);
            }
        }
        return flags;
    }

    private static int parseRange(String s, String name, int min, int max) {
        int value;
        try {
            value = Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            throw new CliError("Invalid --" + name + " '" + s + "'. Provide a whole number between "
                    + min + " and " + max + ".");
        }
        if (value < min || value > max) {
            throw new CliError("--" + name + " must be between " + min + " and " + max + ".");
        }
        return value;
    }

    private static String[] resolveCredentials(Map<String, String> flags) {
        String token = firstNonBlank(flags.get("token"), System.getenv("TMDB_API_TOKEN"));
        String apiKey = firstNonBlank(flags.get("api-key"), flags.get("api_key"), System.getenv("TMDB_API_KEY"));
        if (token == null && apiKey == null) {
            throw new CliError("""
                    No TMDB credentials found.
                      Get a free API key at https://www.themoviedb.org/settings/api, then either:
                        export TMDB_API_KEY=your_v3_api_key     (or pass --api-key <key>)
                        export TMDB_API_TOKEN=your_v4_token     (or pass --token <token>)""");
        }
        return new String[]{apiKey, token};
    }

    // ---- Small helpers ----

    private static String year(String releaseDate) {
        if (releaseDate == null || releaseDate.isBlank()) {
            return null;
        }
        String d = releaseDate.trim();
        if (d.length() < 4) {
            return null;
        }
        // Only treat the leading 4 characters as a year if they're all digits;
        // otherwise a malformed date would print junk like "Title (abc)".
        for (int i = 0; i < 4; i++) {
            if (!Character.isDigit(d.charAt(i))) {
                return null;
            }
        }
        return d.substring(0, 4);
    }

    private static String truncate(String s, int max) {
        String trimmed = s.strip();
        if (trimmed.length() <= max) {
            return trimmed;
        }
        int cut = max - 1;
        // Don't cut in the middle of a surrogate pair (e.g. an emoji).
        if (cut > 0 && Character.isLowSurrogate(trimmed.charAt(cut))
                && Character.isHighSurrogate(trimmed.charAt(cut - 1))) {
            cut--;
        }
        return trimmed.substring(0, cut).stripTrailing() + "…";
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return null;
    }

    private static String pad(String s, int width) {
        return s.length() >= width ? s : " ".repeat(width - s.length()) + s;
    }

    // ---- Colour helpers ----

    private static String color(String code, String s) {
        return COLOR ? "[" + code + "m" + s + "[0m" : s;
    }

    private static String bold(String s) {
        return color("1", s);
    }

    private static String cyan(String s) {
        return color("36", s);
    }

    private static String yellow(String s) {
        return color("33", s);
    }

    private static String dim(String s) {
        return color("2", s);
    }
}
