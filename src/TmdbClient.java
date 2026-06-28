import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Thin client over the TMDB REST API (v3).
 *
 * Supports both authentication styles: a v3 {@code api_key} query parameter and
 * a v4 read access token sent as a Bearer header. Provide one of them.
 * See https://developer.themoviedb.org/reference/intro/getting-started.
 */
final class TmdbClient {

    private static final String BASE_URL = "https://api.themoviedb.org/3";

    private final HttpClient http;
    private final String apiKey;      // v3 query-param key (nullable)
    private final String bearerToken; // v4 read access token (nullable)

    TmdbClient(String apiKey, String bearerToken) {
        this.apiKey = apiKey;
        this.bearerToken = bearerToken;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /** Fetches one page of the given listing. */
    List<Movie> fetch(MovieType type, int page) {
        String url = BASE_URL + "/movie/" + type.endpoint + "?language=en-US&page=" + page;
        // Prefer the bearer token; otherwise fall back to the api_key query parameter.
        if (bearerToken == null && apiKey != null) {
            url += "&api_key=" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8);
        }
        HttpResponse<String> response = send(url);
        if (response.statusCode() != 200) {
            throw apiError(response);
        }
        return parseResults(response.body());
    }

    private HttpResponse<String> send(String url) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json")
                .header("User-Agent", "tmdb-cli-tool")
                .GET();
        if (bearerToken != null) {
            builder.header("Authorization", "Bearer " + bearerToken.trim());
        }
        try {
            return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            String reason = (e.getMessage() != null) ? e.getMessage() : e.getClass().getSimpleName();
            throw new CliError("Could not reach TMDB (" + reason
                    + "). Check your internet connection and try again.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CliError("The request to TMDB was interrupted.");
        }
    }

    private static CliError apiError(HttpResponse<String> response) {
        int code = response.statusCode();
        String message = extractMessage(response.body());
        if (code == 401) {
            String detail = (message != null) ? message.strip() : "invalid API key/token";
            if (detail.endsWith(".")) {
                detail = detail.substring(0, detail.length() - 1);
            }
            return new CliError("TMDB rejected the request (401): " + detail
                    + ". Double-check your TMDB credentials.");
        }
        if (code == 429) {
            return new CliError("TMDB rate limit reached (429). Please wait a moment and try again.");
        }
        String base = "TMDB API request failed (HTTP " + code + ")";
        return new CliError(message != null ? base + ": " + message : base + ".");
    }

    private static String extractMessage(String body) {
        try {
            if (Json.parse(body) instanceof Map<?, ?> map && map.get("status_message") instanceof String s) {
                return s;
            }
        } catch (RuntimeException ignored) {
            // body was not JSON; no message to extract
        }
        return null;
    }

    /** Parses a TMDB movie-list response body into a list of movies. Package-private for testing. */
    static List<Movie> parseResults(String body) {
        Object root;
        try {
            root = Json.parse(body);
        } catch (RuntimeException e) {
            throw new CliError("Could not parse TMDB's response: " + e.getMessage());
        }
        if (!(root instanceof Map<?, ?> map) || !(map.get("results") instanceof List<?> results)) {
            throw new CliError("Unexpected response from TMDB (missing 'results' array).");
        }
        List<Movie> movies = new ArrayList<>();
        for (Object o : results) {
            if (o instanceof Map<?, ?> m) {
                movies.add(new Movie(
                        str(m.get("title")),
                        str(m.get("release_date")),
                        dbl(m.get("vote_average")),
                        intOf(m.get("vote_count")),
                        str(m.get("overview")),
                        str(m.get("original_language"))));
            }
        }
        return movies;
    }

    private static String str(Object o) {
        return (o instanceof String s) ? s : null;
    }

    private static double dbl(Object o) {
        return (o instanceof Number n) ? n.doubleValue() : 0.0;
    }

    private static int intOf(Object o) {
        return (o instanceof Number n) ? n.intValue() : 0;
    }
}
