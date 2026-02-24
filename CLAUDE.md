# CLAUDE.md

## Build & Test
- `./gradlew build` — compile, test, javadoc, coverage verification
- `./gradlew test` — run tests only
- `./gradlew javadoc` — generate documentation
- JaCoCo enforces 80% minimum code coverage

## Project Structure
- `app/src/main/java/com/ruleengine/` — source root
  - `rule/` — Rule, Condition, Operator, RuleLoader
  - `url/` — ParsedUrl, UrlParser, UrlPart
  - `index/` — Trie, AhoCorasick, RuleIndex, ContainsStrategy
  - `engine/` — RuleEngine, BatchProcessor
  - `App.java` — CLI entry point
- `app/src/test/java/com/ruleengine/` — tests mirror source structure
- `gradle/libs.versions.toml` — dependency version catalog

## Code Style
- Follow the Google Java Style Guide: https://google.github.io/styleguide/javaguide.html
- Java 25, Gradle with version catalogs
- Javadoc on public classes and public methods

## Benchmarking & Profiling
- `./gradlew benchmark` — run rule engine throughput benchmark (~200K URLs, ~2000 rules)
- `./gradlew profileBenchmark -PasyncProfilerLib=<path>` — run benchmark with async-profiler (collapsed stacks output)
  - Optional: `-PprofileEvent=alloc` for allocation profiling (default: `cpu`)
- `python3 scripts/profile.py` — run profiler and print text summary
- `python3 scripts/profile.py --analyze <file>` — analyze existing collapsed stacks file
- Profile output: `app/build/benchmark/profile.collapsed`

## Git Workflow
- Trunk-based development: short-lived feature branches, squash-merge to `main`
- Branch protection on `main`: PRs required, CI must pass
- Do not commit unless explicitly asked
- Always run tests before creating a PR
- Keep PRs under 200 lines of code; 400 lines max in exceptional cases
- Never use heredocs or multiline strings in Bash commands (glob patterns don't match across newlines)
- Git commits: use single-line `-m "message"` or multiple `-m` flags for paragraphs
- PR creation: write the body to a temp file, then use `gh pr create --body-file <file>`
- Always ask before running `gh pr merge --admin`
