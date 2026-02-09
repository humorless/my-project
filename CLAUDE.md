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

**Database:** `db.clj` provides `exec!` and `exec-one!` helpers that use HoneySQL for query building and next.jdbc for execution. Results are returned as unqualified kebab-case maps. **Important:** `db/exec!` and `db/exec-one!` only accept HoneySQL maps (e.g. `{:select [:*] :from [:users]}`), NOT raw SQL strings. For raw SQL, use `next.jdbc/execute!` directly (e.g. `(jdbc/execute! ds ["SELECT * FROM sqlite_master"])`).

**Accessing the datasource in brepl:** Get it from the running Integrant system: `(:my-project.db/db integrant.repl.state/system)`. The dev system also includes `:integrant-extras.process/process` for managed subprocesses (e.g. `bb css-watch`).

**Dev hot-reload:** In dev mode, `wrap-reload` in `server.clj` re-creates the Reitit router from the `my-project.routes/routes` var on every request. This means editing handler/route source files takes effect immediately — no server restart needed. You can also use `alter-var-root` on the routes var via nREPL/brepl for live changes (nREPL port is in `.nrepl-port`).

**Dev REPL workflow** (`dev/user.clj`): `(reset)` to start/restart system, `(stop)` to halt, `(run-all-tests)` to run tests from REPL. To run a single test namespace: `(eftest/run-tests (eftest/find-tests 'my-project.home-test) {:report eftest-report/report})`.

**Testing:** Tests in `test/` use `integrant-extras.tests/with-system` fixture to boot a test system. `test-utils.clj` provides helpers: `with-truncated-tables` cleans DB between tests, `response->hickory` parses HTML responses for assertions.

## Dev Workflow

- The nREPL is usually running during development. Prefer using brepl to verify changes — reload the namespace and call the handler directly to confirm behavior.
- After editing source files, use brepl to `require` the changed namespace with `:reload` and test interactively, rather than relying solely on `curl` or `bb test`.
- **Debugging** — When investigating a bug, use brepl to explore state before modifying code: check `*e` for the last exception, call functions directly to inspect return values and data shapes, query the DB with `(db/exec! ds ...)`, and inspect the Integrant system via `integrant.repl.state/system`.
- **Exploration** — When working with unfamiliar code, use brepl to call `(doc fn-name)`, `(source fn-name)`, or invoke functions directly to understand behavior, rather than relying only on reading source files.
- **Live system operations** — Use brepl for tasks like inserting test data, running migrations, or checking connection pool status against the running dev system.

### Interactive Development Techniques

- **Call handlers directly** — Ring handlers are pure functions (request map → response map). Test them in brepl without going through HTTP or middleware: `(handlers/home-handler {})`, `(handlers/inspect-handler {})`. Inspect response structure with `(keys resp)` before dumping the full body.
- **Verify routes with Reitit** — Use `reitit.core/match-by-path` to check route matching interactively without a browser: `(reitit.core/match-by-path (reitit.ring/router app-routes/routes) "/")`. Useful for verifying newly added routes before integration testing.
- **Compose and inspect HoneySQL queries** — Build queries incrementally as data and preview the generated SQL with `(honey.sql/format query {:quoted true})` before sending to the database. This lets you verify correctness without executing against the DB.

### Balanced REPL Exploration (Token Optimization)

- **Incremental Inspection** — Always start with low-token queries: use `(keys ...)` to see structure, `(count ...)` for size, or `(take 5 ...)` for samples. 
- **Escalation Path** — If sampling is insufficient to diagnose the issue, you are encouraged to retrieve specific nested data or full objects, but do so purposefully (e.g., `(get-in ...)`).
- **Efficient Debugging** — Start with `(ex-message *e)` and `(ex-data *e)`. Only dump a truncated stack trace with `(clojure.repl/pst 10)` if the root cause remains hidden.
- **State Verification** — After modifying code, use brepl to call the affected functions directly. If the output is a massive data structure, summarize the key changes for the session instead of printing the whole thing.

## Code Style

- Formatting: cljfmt with `{:parallel? true, :sort-ns-references? true, :function-arguments-indentation :cursive}` (see `.cljfmt.edn`)
- Linting: clj-kondo with warnings for `:single-key-in`, `:shadowed-var`; `:refer-all` allowed only for `clojure.test`
- JS assets are vendored (no npm/node build step)
