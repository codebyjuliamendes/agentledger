# AgentLedger

An approval gate and audit trail for AI agent actions. Every proposal is classified by risk and recorded with its target, rationale, policy result, and human review events.

## Run locally

```bash
npm install
npm start
```

Open `http://localhost:3009`. Seeded actions demonstrate low-risk automatic approval, human review, high-risk denial, and critical policy blocking.

## Product scope

This MVP simulates execution and never changes an external system. Its lightweight risk rules are illustrative, not a security boundary. A production product should use explicit per-tool policies, authenticated human identities, signed event records, durable retries, and integrations that wrap agent tool calls.

## API

- `GET /api/actions` and `GET /api/summary`
- `GET /api/actions/:id/events`
- `POST /api/actions` — evaluate and record a proposal
- `POST /api/actions/:id/review` — approve or deny a pending/blocked action

Stack: Express, SQLite, and a responsive vanilla JavaScript dashboard.
