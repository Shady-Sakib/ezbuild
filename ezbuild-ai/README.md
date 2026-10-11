# EZBuild AI Service

An optional microservice that runs the assistant's two LLM calls for the store:

1. `POST /v1/extract` turns a customer message into a typed requirements record.
2. `POST /v1/explain` writes the reply from catalogue facts supplied by the store.

Running it separately keeps the Gemini key out of the store and lets the AI side scale or restart on its own. The store never sends personal data (emails, phone numbers, addresses) here.

## Run

```bash
export GEMINI_API_KEY=...            # Google AI Studio key
export ADVISOR_KEY=some-long-secret  # shared with the store
mvn spring-boot:run                  # listens on :1020
```

Then point the store at it in the store's `.env`:

```properties
ADVISOR_MODE=remote
ADVISOR_URL=http://localhost:1020
ADVISOR_KEY=some-long-secret
```

If this service is down or slow, the store's circuit breaker switches the assistant to its rule-based mode automatically.

## Configuration

| Variable | Default | Purpose |
|---|---|---|
| `PORT` | `1020` | HTTP port |
| `ADVISOR_KEY` | `local-dev-service-key` | Required in the `X-Service-Key` header on every `/v1/**` call. **Change it outside local development** |
| `GEMINI_API_KEY` | – | Google AI Studio key |
| `GEMINI_MODEL` | `gemini-flash-latest` | Gemini model name |

Prompts live in `src/main/resources/prompts/`. Keep them in sync with the store's copies in `ezbuild/src/main/resources/prompts/`.

`GET /actuator/health` is open for health checks.
