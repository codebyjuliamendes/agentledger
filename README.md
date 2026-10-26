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
- `POST /api/actions/:id/review` — approve or deny a pending action; blocked critical actions cannot be approved

Stack: Express, SQLite, and a responsive vanilla JavaScript dashboard.

## Configurable risk routing

Open **Policy settings** to configure comma-separated terms routed as medium or high risk. Medium- and high-risk proposals require a human decision; low-risk proposals are recorded as simulated. Destructive terms are always classified as critical and blocked, and the review API refuses approval. Policy settings persist in SQLite. This demonstration does not execute external operations or replace authenticated production authorization.

