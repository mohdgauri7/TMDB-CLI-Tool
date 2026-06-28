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
- Clear output: rank, title (year), rating, vote count, original language, and overview.
- Robust error handling for invalid input, missing/invalid credentials, network
  failures, and API errors (the TMDB error message is shown).
- Credentials via a gitignored `.env` file, environment variables, or flags;
  supports both a v3 API key and a v4 read access token.

## Requirements

- A JDK 21+ on your `PATH` (`javac` / `java`).
- TMDB credentials (free) — a v3 API key or a v4 read access token. See
  [Setup](#setup) below.

## Setup

You need TMDB credentials (free). Create an account and grab them at
<https://www.themoviedb.org/settings/api> — either a **v3 API key** or a
**v4 read access token** (the token is preferred and sent as a Bearer header).
Provide them in whichever way is most convenient:

- **`.env` file (easiest):** create a `.env` in this directory; the launcher
  auto-loads it on every run. It's gitignored, so the key is never committed.

  ```dotenv
  TMDB_API_TOKEN="your_v4_read_access_token"
  TMDB_API_KEY="your_v3_api_key"
  ```

- **Environment variables:** `export TMDB_API_TOKEN=...` and/or `export TMDB_API_KEY=...`

- **Per-run flags:** `--token <token>` and/or `--api-key <key>`

Precedence: command-line flags override the `.env` file, which overrides any
pre-existing environment variables. When both a token and a key are available,
the v4 token is used.

## Install / Run

From this directory, use the launcher (compiles on first run / on change, and
auto-loads `.env`). Once your credentials are set up, just run:

```bash
./tmdb-app --type popular
```

You can also compile and run manually:

```bash
javac -d out src/*.java
java -cp out TmdbApp --type popular
```

> Running `java` directly skips the launcher, so it won't auto-load `.env` —
> export the environment variables or pass `--token` / `--api-key` yourself.

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

1. Some Movie Title (2024)   ★ 8.3 (1,234 votes)  ·  EN
   A short overview of the movie, truncated if it gets too long for the line…

2. Another Movie (2023)   ★ 7.9 (980 votes)  ·  JA
   (no overview)
```

(The trailing `· EN` / `· JA` is the movie's original language; it's omitted
when TMDB doesn't provide one. Colours are applied in an interactive terminal.)

## Notes

- The `--type` aliases `now_playing` / `now-playing` and `top_rated` / `top-rated`
  are also accepted.
- Colours are shown in an interactive terminal and disabled automatically when
  output is piped (or when `NO_COLOR` is set).
- If you use a `.env` file it stays on your machine and is gitignored, so your
  key is never committed. Prefer env vars or `--token` / `--api-key` if you'd
  rather not keep the key in a file at all.
