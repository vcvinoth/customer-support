# Customer Support

A customer support chat service built with Spring Boot and Spring AI. It connects to a
local Ollama model, augments answers with retrieved policy documents (RAG via
PostgreSQL + pgvector), and lets the model call tools to look up real customer/order
data, issue refunds, or escalate to a human — with an Angular chat UI on top.

## Features

- Chat endpoint backed by a local Ollama model (`llama3.2` by default)
- Retrieval-augmented answers over a small seeded knowledge base (returns/shipping/refund/account policy docs), stored in Postgres via pgvector
- Tool-calling agent: looks up orders/customers, issues refunds (with real eligibility checks), or escalates to a human — the model decides which to use
- Per-conversation chat memory, so follow-up questions have context
- Customer/order schema managed by Flyway, backed by Postgres
- Angular single-page chat UI

## Prerequisites

- **Java 21** (a Maven wrapper is included, so a separate Maven install isn't required)
- **Docker** (Docker Desktop or equivalent) — used to run Postgres via Docker Compose, auto-started by the app
- **[Ollama](https://ollama.com)**, installed and running locally, with these models pulled:
  ```
  ollama pull llama3.2
  ollama pull nomic-embed-text
  ```
- **Node.js + npm** — only needed for the Angular frontend (`frontend/`)

## Running the backend

```
./mvnw spring-boot:run
```

On startup this will:
- auto-start a `pgvector/pgvector:pg16` Postgres container via Docker Compose (`docker-compose.yml`), listening on `localhost:5432`
- run Flyway migrations to create the `customers`/`orders` tables
- initialize the pgvector `vector_store` table and ingest the seed knowledge base (`src/main/resources/rag-docs/*.md`) on first run

The API is then available at `http://localhost:8080`. Try it directly:

```
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"prompt":"What is your return policy?","conversationId":"test-1"}'
```

`conversationId` is optional — omit it and every request shares one default conversation;
send a consistent id per session to keep multi-turn context.

### Example prompts to try

The seed data includes one customer (`jane@example.com`, id `1`) with one order
(id `1`, already `REFUNDED`). Try these against the `/api/chat` endpoint or the UI:

**Policy questions (RAG, no order/email needed)**
- "What is your return policy?"
- "How long does shipping usually take?"
- "How do refunds get processed once approved?"
- "How do I reset my account password?"
- "Do you ship internationally?"

**Order lookup (tool calling)**
- "What's the status of order 1?"
- "Can you list all orders for jane@example.com?"
- "What orders does customer 1 have?"
- "What's the status of order 999?" (tests the not-found path)

**Refund flow (tool calling + business rules)**
- "I'd like a refund for order 1, it arrived damaged." (should say it's already refunded)
- "Can you refund order 1 again?" (tests the duplicate-refund guard)
- "I want to return something I bought over a month ago, order 1." (tests the 30-day window logic — needs a fresh, non-refunded order)

**Escalation**
- "I've been charged twice and support hasn't responded in a week, I need to talk to a real person."
- "This is unacceptable, escalate me to a human agent."

**Multi-turn memory** (send with the same `conversationId`)
1. "My email is jane@example.com, what orders do I have?"
2. "Is the first one eligible for a refund?" (should retain context from turn 1)

**Guardrail / hallucination check**
- "What's your policy on refunding gift cards?" (not covered by the seeded policy docs — should say it doesn't know rather than invent one)
- "What's the status of order 1, and also tell me your CEO's name?" (off-topic mixed in)

## Running the frontend

In a separate terminal, with the backend already running:

```
cd frontend
npm install
npm start
```

This runs `ng serve` with a dev proxy (`proxy.conf.json`) that forwards `/api/*` calls to
the backend on port 8080. Open **http://localhost:4200**.

## Running the whole stack in Docker

`docker-compose.full.yml` containerizes everything — Postgres, Ollama (with models
pulled automatically), the backend, and the frontend — so nothing needs to be installed
on the host except Docker:

```
docker compose -f docker-compose.full.yml up --build
```

Open **http://localhost:4200**. First run takes a few minutes (pulling ~2.3GB of Ollama
models into the container); subsequent runs reuse the named volumes.

This is a separate file from `docker-compose.yml` on purpose: `docker-compose.yml` stays
Postgres-only so Spring Boot's own Docker Compose support (used by `./mvnw
spring-boot:run` above) doesn't try to also manage Ollama/backend/frontend containers.
Don't run both at once — they'd fight over the same ports (5432, 8080, 4200, 11434).

## Running tests

Backend:

```
./mvnw test
```

Runs three test classes:
- `ApplicationTests` — context-load smoke test
- `ChatControllerTest` — controller test with mocked `ChatClient`/tools/vector store
- `ChatIntegrationTest` — real end-to-end test against the live `/api/chat` endpoint: seeds a customer/order, then verifies real tool calling, real RAG retrieval, and real conversation memory all work together

All three use Testcontainers to spin up a real disposable Postgres (with pgvector) —
this requires Docker. `ChatIntegrationTest` additionally calls your real local Ollama, so
it needs Ollama running with `llama3.2` and `nomic-embed-text` pulled, same as running
the app. Because it's a real (small, local) model, responses aren't perfectly
deterministic; `spring.ai.ollama.chat.options.temperature` is set low to keep this
reliable in practice.

Frontend:

```
cd frontend
npm test
```

## Configuration

Key settings in `src/main/resources/application.properties`:

| Property | Purpose |
|---|---|
| `spring.ai.ollama.chat.options.model` | Chat model (default `llama3.2`) |
| `spring.ai.ollama.chat.options.temperature` | Sampling temperature (default `0.1`) — kept low for consistent, predictable support-agent behavior |
| `spring.ai.ollama.embedding.options.model` | Embedding model for RAG (default `nomic-embed-text`, 768 dimensions) |
| `spring.ai.vectorstore.pgvector.dimensions` | Must match the embedding model's output size |
| `spring.datasource.url` / `.username` / `.password` | Postgres connection (matches `docker-compose.yml` by default) |

## Project layout

```
src/main/java/com/customersupport/
  Application.java            entry point
  ChatController.java          /api/chat endpoint, wires system prompt + tools + RAG + memory
  CustomerSupportTools.java    @Tool methods: order/customer lookup, refunds, escalation
  KnowledgeBaseIngestor.java   seeds the vector store from rag-docs/*.md on first run
  Customer.java / Order.java   JPA entities
  CustomerRepository.java / OrderRepository.java

src/main/resources/
  application.properties
  db/migration/                Flyway migrations (customers/orders schema)
  rag-docs/                    seed policy documents for RAG

src/test/java/com/customersupport/
  ApplicationTests.java         context-load smoke test
  ChatControllerTest.java       controller test with mocked dependencies
  ChatIntegrationTest.java      real end-to-end test against the live endpoint
  TestcontainersConfiguration.java   shared Postgres/pgvector test container

frontend/                      Angular chat UI
  Dockerfile / nginx.conf       container image for the full-stack compose file

Dockerfile                     backend container image
docker-compose.yml             Postgres only, for local dev (auto-managed by Spring Boot)
docker-compose.full.yml        full stack (postgres, ollama, backend, frontend)
```
