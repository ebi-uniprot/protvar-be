# ProtVar Back-end

The REST API for **ProtVar**, UniProt's variant annotation tool at EBI. It maps
genomic and protein variants to their functional, structural, population and
predicted-effect annotations and serves them to the [ProtVar
front-end](https://github.com/ebi-uniprot/protvar-fe) and to programmatic
consumers.

- Spring Boot 3.3 application, Java 21
- Main class: `uk.ac.ebi.protvar.ApplicationMainClass`
- API base path: `/ProtVar/api`
- Interactive API docs (Swagger UI): served at `/` — OpenAPI JSON at `/docs`
- Live service: <https://www.ebi.ac.uk/ProtVar>

## Architecture

See [ARCHITECTURE.md](ARCHITECTURE.md) for the search model, query optimiser,
counting modes and semantic search design. CSV/VCF I/O formats are documented
under [`docs/`](docs/).

## Dependencies

ProtVar BE talks to a small set of **backing services** and **no third-party
REST APIs**.

### Backing services (required)

| Service | Purpose | Config |
|---|---|---|
| PostgreSQL | All annotation data — mapping, scores, predictions, functional/population/structural caches. The single source of truth at request time. | `protvar.datasource.*` |
| RabbitMQ | Queue for asynchronous full-result download jobs. | `spring.rabbitmq.*` |
| Redis | Caching and download-job status tracking. | `spring.data.redis.*` |
| SMTP (`smtp.ebi.ac.uk`) | Sends "download ready" notification emails. | `spring.mail.*` |

### Internal HTTP calls (optional / non-blocking)

| Target | Used by | Notes |
|---|---|---|
| Embedding service(s) — `embedding-service-<model>:8000` | `EmbeddingClient`, semantic search | One in-cluster service per model (see `embedding.properties`). Called to embed the free-text query. If unreachable, semantic search degrades; the rest of the API is unaffected. |
| protvar-mcp — `protvar-mcp.url` | `McpHealthService` | Health ping only (`/actuator/health`), surfaced by the public status endpoint. Failure is tolerated quietly. |

### No external API dependencies

ProtVar BE makes **no calls to third-party REST APIs**. Earlier versions
queried the UniProt **Proteins API**, the UniProt **Variation API** and the
**PDBe API** at request time. That data is now imported into PostgreSQL ahead
of time by the import pipelines (`protvar-import`, `protvar-import-py`) and
read directly from the database, so the BE has no runtime dependency on those
services.

## Running locally

1. Clone the repo.
2. Create `src/main/resources/application-local.properties` (git-ignored) and
   set at least the datasource:
   ```properties
   protvar.datasource.jdbc-url=jdbc:postgresql://<host>:<port>/<db>?options=-c%20search_path=protvarwrite,public
   protvar.datasource.username=<user>
   protvar.datasource.password=<password>

   spring.rabbitmq.host=localhost
   spring.data.redis.host=localhost
   ```
   The `local` profile is active by default (`spring.profiles.active=local`).
3. Start the app:
   ```sh
   mvn spring-boot:run
   ```
4. Open <http://localhost:8080/ProtVar/api/> for Swagger UI.

RabbitMQ and Redis are only needed to exercise the download flow. Semantic
search additionally needs a reachable embedding service (`embedding.service.url`).

## Tests

```sh
mvn test
```

## Deployment

The GitHub repo is mirrored to GitLab EBI, where the CI/CD pipeline builds and
deploys. Deployment is branch-driven:

| Branch | Environment | Database |
|---|---|---|
| `int` | internal | Prod DB |
| `dev` | development (beta) | Dev DB |
| `main` | live / public (+ fallback) | Main DB |

Merge or commit to the relevant branch and the GitLab pipeline deploys it. A
public release on `main` deploys to both the live (`pub`) and `fallback`
clusters. The mirror can take a couple of minutes to refresh from GitHub. To check a
deployment, use the GitLab CI/CD view or `helm status protvar-be` and inspect
the last-deployed timestamp.

Health/readiness probes run on the management connector (port 8081):
`/actuator/health/liveness` and `/actuator/health/readiness`.
