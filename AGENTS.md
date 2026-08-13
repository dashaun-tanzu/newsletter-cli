# AGENTS.md

Guidance for AI agents working in this repository.

## What this is

A Spring Shell (4.x) / Spring Boot (4.x) command-line app that assembles a markdown
"Spring newsletter" document by pulling content from external sources. There is no UI
and no web server (`spring.main.web-application-type=none`); commands run non-interactively
(`spring.shell.interactive.enabled=false`), so each invocation is one shell command and exit.

## Build / run / test

```bash
./mvnw clean package                       # build the runnable jar
./mvnw spring-boot:run                      # run during development
java -jar target/newsletter-0.0.1-SNAPSHOT.jar <command> [options]

./mvnw test                                 # all tests
./mvnw test -Dtest=RssServiceTest           # one test class
./mvnw test -Dtest=RssServiceTest#methodName  # one test method

./mvnw -Pnative native:compile              # GraalVM native image (recent commits track native fixes)
```

Targets **Java 21** (`pom.xml` `java.version`). Use SDKMAN to switch JDKs.

## Architecture

Single package `dev.dashaun.cli.newsletter`. The shape is a thin command layer over
independent source-fetchers, with one service owning all file mutation:

- **`DocumentCommands`** — the only `@Command` class; every CLI verb lives here. It does no
  fetching or file I/O itself — it wires fetcher services to `DocumentService` and formats the
  string result. Each command catches its own exceptions and returns a human-readable string
  (commands never throw to the shell).
- **Fetcher services** (`@Service`, one external source each): `RssService` (Spring blog
  Atom feeds, ROME), `CalendarService` (Spring release iCal, ical4j), `YouTubeService`
  (per-channel YouTube RSS, ROME), `GitHubService` (org repos via GitHub REST). Each returns
  a small data type (record or static nested class) whose `toString()` renders the markdown
  bullet line for that item — formatting lives on the data type, not in `DocumentService`.
- **`DocumentService`** — owns ALL reads/writes of the markdown file. It locates sections by
  regex (`## News:`, `## Recent Enterprise Releases:`, `## Releases coming soon:`, `## YouTube:`,
  `## Demos:`), replaces the body between the heading and the next blank line / next `##`, and
  always runs `writeDocumentWithCleanup` (collapses 3+ blank lines). Section patterns use
  `Pattern.DOTALL`; if a section is missing, methods fall back to inserting it relative to a
  neighboring section.
- **`RetryUtils`** — shared static retry-with-exponential-backoff used by every fetcher.
  `isRetryableException` walks the cause chain and retries on 5xx / 408 / 429 /
  `WebClientRequestException` / `TimeoutException` / `IOException`. Callers can pass a custom
  predicate — `YouTubeService` extends it to also retry transient 404s (its feed endpoint
  404s for tens of seconds), which is why YouTube uses a longer window (5 attempts, 5s initial
  backoff) than RSS/GitHub (3 attempts, 2s). Every sleep gets ±25% jitter (fetchers hit several
  endpoints on one host in sequence; without jitter the retries arrive in lockstep), is capped at
  `MAX_BACKOFF` (60s), and yields to a numeric `Retry-After` response header when the server
  sends one.
- **`ExitCodeTracker`** — an `ExitCodeGenerator` bean letting a command report a degraded run
  without aborting the rest of the document. `NewsletterApplication.main` wraps
  `SpringApplication.run` in `SpringApplication.exit`, so a marked failure becomes a non-zero
  process exit. Without it a command returns a String and always exits 0, and a caller cannot
  tell a failed run from a good one.

## Conventions worth preserving

- **The markdown document is the data model and the output.** Section headings are load-bearing;
  changing a heading string means changing the matching regex in `DocumentService`. The document
  layout is created by `createNewDocument`.
- **Fetchers degrade gracefully, never fatally — but an outage is not an empty result.**
  `RssService` and `YouTubeService` skip a failing feed/channel (log to stderr, continue) rather
  than aborting the whole command. Preserve this — one dead source shouldn't sink an entire
  `full-update`. What must NOT happen is returning an empty collection when *every* source failed:
  that is indistinguishable from "nothing new", and it caused the newsletter repo to publish a
  blank YouTube section. See below.
- **Default news feeds** are a comma-separated CSV constant `DocumentCommands.DEFAULT_NEWS_FEEDS`
  (four spring.io Atom feeds), split + deduped by link in `RssService.fetchLatestNews`. Default
  news limit is 8.
- **GitHub demos** come from the hardcoded org `dashaun-tanzu` (`GitHubService.ORG_NAME`), filtered
  to repos ending in `-demo`, excluding archived, sorted by `updated_at`. JSON is parsed with regex,
  not Jackson (deliberate, to keep the native image lean) — extend the regex carefully if fields change.
- **YouTube channels** are a hardcoded list in `YouTubeService.CHANNELS`; Shorts are filtered out
  (`isShort` checks for `/shorts/` in the link).

## YouTube reliability

The feed endpoint returns transient 404s, and when it does it typically fails for **all** channels
at once, for minutes at a time. The retry budget alone cannot fix that, so the failure is made
visible instead of silently producing an empty section:

- Budget is 5 attempts at 5s→10s→20s→40s, ~75s per channel. Channels run sequentially, so a total
  outage takes ~3m45s to declare — a `full-update` that takes ~4 minutes is this failure happening.
- A named `User-Agent` is sent. Reactor Netty's default agent is throttled harder on shared CI
  egress addresses, which is where the 404s cluster.
- One channel failing is skipped. **Every** channel failing throws `YouTubeUnavailableException`.
- `DocumentCommands` catches that exception **inline**, not in the outer `full-update` try/catch —
  letting it reach the outer handler would skip the demos section that runs after YouTube. On
  failure it leaves the existing section untouched instead of overwriting it with an empty list,
  and marks `ExitCodeTracker` so the process exits non-zero.

## Testing

JUnit 5 + Spring Boot test. External HTTP is stubbed with **WireMock** (`wiremock-jre8-standalone`);
shell command tests use `spring-shell-starter-test`. When adding a fetcher, add a WireMock-backed
service test mirroring the existing `*ServiceTest` classes — do not hit live endpoints in tests.

`YouTubeService` has a package-private `(feedBaseUrl, maxAttempts, initialBackoff)` constructor so
tests can point at WireMock and shorten the retry budget; production uses the no-arg constructor.
Without that seam the failure tests would sit through the real 225s budget. Keep new retry-related
tests hermetic the same way.

Note: `CLAUDE.md` is gitignored here — **this file is the committed guidance.** Mirror anything
durable into it.
