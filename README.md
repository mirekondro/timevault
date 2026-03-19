# TimeVault

TimeVault is a local-first Java desktop app for Hack Esbjerg 2026. It captures URLs, text, and images into a SQLite archive, writes a three-sentence AI context note, rescues dead links from the Wayback Machine, and stores Denmark Today vibe capsules from Danish news feeds.

## Run

```bash
./mvnw javafx:run
```

## Build

```bash
./mvnw package
```

## AI Providers

TimeVault works without an API key by falling back to a local context generator. To use a real model, set one of these environment variables before launching:

```bash
GEMINI_API_KEY=...
```

or

```bash
ANTHROPIC_API_KEY=...
```

Optional:

```bash
TIMEVAULT_AI_PROVIDER=gemini
TIMEVAULT_AI_PROVIDER=anthropic
```

## Storage

All local data is stored in:

```text
~/.timevault
```

That folder contains the SQLite database, rescued HTML snapshots, copied images, and exported archive files.
