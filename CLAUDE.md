# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

A Clojure web application generated with [clojure-stack-lite](https://github.com/abogoyavlensky/clojure-stack-lite). Server-side rendered with HTMX + AlpineJS for interactivity, TailwindCSS v4 for styling, SQLite for database, and Kamal for deployment.

## Common Commands

All commands use Babashka (`bb`) as the task runner:

- `bb clj-repl` — Start Clojure REPL with dev+test aliases, then call `(reset)` to start the system
- `bb test` — Run all tests (uses cloverage + eftest)
- `bb fmt-check` / `bb fmt` — Check / fix code formatting (cljfmt)
- `bb lint` — Lint code with clj-kondo (run `bb lint-init` first on fresh clone)
- `bb outdated-check` — Check for outdated dependencies
- `bb check` — Run fmt + lint + outdated + test in sequence (note: uses `fmt` which auto-fixes, and `outdated` which upgrades)
- `bb css-watch` — Watch and rebuild TailwindCSS (auto-started in dev via `config.dev.edn`)
- `bb css-build` — Build minified CSS for production
- `bb build` — Build uberjar (runs css-build + asset hashing)
- `bb fetch-assets` — Re-download vendored JS assets (htmx, alpinejs)
- `bb deps` — Pre-download all Clojure dependencies

## Tooling Setup

Tools are managed via [mise](https://mise.jdx.dev/) (see `.mise.toml`): Java (Temurin 21), Clojure, Babashka, clj-kondo, cljfmt, TailwindCSS. Run `mise trust && mise install` to set up.

## Architecture

**Integrant-based system** — The app uses [Integrant](https://github.com/weavejester/integrant) for component lifecycle management, extended by `integrant-extras`.

- `resources/config.edn` — System configuration with profile-based values (`:dev`, `:test`, `:prod`). Two components: `::db/db` and `::server/server`.
- `resources/config.dev.edn` — Dev overlay, merges base config and adds `bb css-watch` as a subprocess.

**Component keys:**
- `:my-project.db/db` — HikariCP connection pool for SQLite. Runs Ragtime migrations on startup from `resources/migrations/`. Uses in-memory SQLite for tests.
- `:my-project.server/server` — Jetty server with Reitit router. Auto-reloads in dev mode.

**Request flow:** `routes.clj` (route definitions) → `handlers.clj` (request handlers) → `views.clj` (Hiccup templates). The server middleware stack in `server.clj` includes CSRF protection, session management, content negotiation, and Malli coercion. The `reitit-extras/wrap-context` middleware injects the Integrant context (including `:db` datasource) into every request, so handlers access the DB via the request map (e.g. `(:db request)`).

**Database:** `db.clj` provides `exec!` and `exec-one!` helpers that use HoneySQL for query building and next.jdbc for execution. Results are returned as unqualified kebab-case maps.

**Dev hot-reload:** In dev mode, `wrap-reload` in `server.clj` re-creates the Reitit router from the `my-project.routes/routes` var on every request. This means editing handler/route source files takes effect immediately — no server restart needed. You can also use `alter-var-root` on the routes var via nREPL/brepl for live changes (nREPL port is in `.nrepl-port`).

**Dev REPL workflow** (`dev/user.clj`): `(reset)` to start/restart system, `(stop)` to halt, `(run-all-tests)` to run tests from REPL. To run a single test namespace: `(eftest/run-tests (eftest/find-tests 'my-project.home-test) {:report eftest-report/report})`.

**Testing:** Tests in `test/` use `integrant-extras.tests/with-system` fixture to boot a test system. `test-utils.clj` provides helpers: `with-truncated-tables` cleans DB between tests, `response->hickory` parses HTML responses for assertions.

## Code Style

- Formatting: cljfmt with `{:parallel? true, :sort-ns-references? true, :function-arguments-indentation :cursive}` (see `.cljfmt.edn`)
- Linting: clj-kondo with warnings for `:single-key-in`, `:shadowed-var`; `:refer-all` allowed only for `clojure.test`
- JS assets are vendored (no npm/node build step)
