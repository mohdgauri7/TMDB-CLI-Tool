/**
 * A movie from a TMDB listing. {@code title}, {@code releaseDate},
 * {@code overview}, and {@code language} may be {@code null}.
 */
record Movie(String title, String releaseDate, double rating, int voteCount, String overview, String language) {}
