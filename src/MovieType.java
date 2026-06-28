import java.util.Locale;

/** The four movie listings TMDB exposes, with their API path and display label. */
enum MovieType {
    PLAYING("now_playing", "Now Playing"),
    POPULAR("popular", "Popular"),
    TOP("top_rated", "Top Rated"),
    UPCOMING("upcoming", "Upcoming");

    final String endpoint;
    final String label;

    MovieType(String endpoint, String label) {
        this.endpoint = endpoint;
        this.label = label;
    }

    /** Maps a user-supplied --type value (with a few friendly aliases) to a type. */
    static MovieType fromArg(String arg) {
        String key = arg.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        return switch (key) {
            case "playing", "now_playing", "nowplaying" -> PLAYING;
            case "popular" -> POPULAR;
            case "top", "top_rated", "toprated" -> TOP;
            case "upcoming" -> UPCOMING;
            default -> throw new CliError("Invalid --type '" + arg
                    + "'. Choose one of: playing, popular, top, upcoming.");
        };
    }
}
