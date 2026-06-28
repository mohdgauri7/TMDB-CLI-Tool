# TMDB CLI Tool

A command-line tool that fetches movie listings from
[The Movie Database (TMDB)](https://www.themoviedb.org/) and displays them in the
terminal. You choose which listing to show — now playing, popular, top rated, or
upcoming.

This is a solution to the
[roadmap.sh "TMDB CLI"](https://roadmap.sh/projects/tmdb-cli) backend project.

## Features

- Fetches data from the TMDB REST API.
- Four listings: `playing`, `popular`, `top`, `upcoming`.
- Clear output: rank, title (year), rating, vote count, and overview.
- Robust error handling for invalid input, missing/invalid credentials, network
  failures, and API errors (the TMDB error message is shown).
- Credentials via environment variable or flag; supports both a v3 API key and a
  v4 read access token.

## Requirements

- A JDK 21+ on your `PATH` (`javac` / `java`).
- A TMDB API key (free). Create an account and request a key at
  <https://www.themoviedb.org/settings/api>.

## Setup

Provide your TMDB credentials in one of these ways (checked in this order):

```bash
# v4 read access token (recommended)
export TMDB_API_TOKEN="your_v4_read_access_token"

# or v3 API key
export TMDB_API_KEY="your_v3_api_key"
```

You can also pass them per-run with `--token` or `--api-key`.

## Install / Run

From this directory, use the launcher (compiles on first run / on change):

```bash
./tmdb-app --type popular
```

You can also compile and run manually:

```bash
javac -d out src/*.java
java -cp out TmdbApp --type popular
```

## Usage

```
tmdb-app --type <playing|popular|top|upcoming> [options]

Options:
  --type <type>      Listing to show: playing, popular, top, upcoming (default: popular)
  --limit <N>        Number of movies to show, 1-20 (default: 10)
  --page <N>         Result page, 1-500 (default: 1)
  --api-key <key>    TMDB v3 API key (or set TMDB_API_KEY)
  --token <token>    TMDB v4 read access token (or set TMDB_API_TOKEN)
  --help             Show help
```

### Examples

```bash
tmdb-app --type "playing"
tmdb-app --type "popular"
tmdb-app --type "top"
tmdb-app --type "upcoming"

# extras
tmdb-app --type popular --limit 5
tmdb-app --type top --page 2
```

### Sample output

```
Popular movies:

1. Some Movie Title (2024)   ★ 8.3 (1,234 votes)
   A short overview of the movie, truncated if it gets too long for the line…

2. Another Movie (2023)   ★ 7.9 (980 votes)
   (no overview)
```

## Notes

- The `--type` aliases `now_playing` / `now-playing` and `top_rated` / `top-rated`
  are also accepted.
- Colours are shown in an interactive terminal and disabled automatically when
  output is piped (or when `NO_COLOR` is set).
- Your API key is read from the environment or flags and is never written to disk.
