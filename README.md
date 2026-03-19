# TimeVault

TimeVault is a local-first Java desktop app for Hack Esbjerg 2026. It captures URLs, text, and images into a Microsoft SQL Server archive, writes a three-sentence AI context note, rescues dead links from the Wayback Machine, and stores Denmark Today vibe capsules from Danish news feeds.

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

## Database Config

Create your local ignored file from the tracked example:

```bash
copy src\main\resources\db.example.properties src\main\resources\db.properties
```

The real `db.properties` file is gitignored, so credentials stay out of GitHub.

## Artifact Storage

Saved HTML snapshots, copied images, and exports still live in:

```text
~/.timevault
```
