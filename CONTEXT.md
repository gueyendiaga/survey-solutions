# CONTEXT.md — Survey Solutions Analytics

## 1. Project objective

Build a web platform that connects to an existing **Survey Solutions Headquarters (HQ)** instance and provides monitoring, statistics, and data visualisation for **any questionnaire** imported into that HQ.

The platform must not be hard-coded for one questionnaire. It should discover questionnaires and their versions, inspect questionnaire metadata, retrieve the relevant survey/interview data, and generate reusable indicators and visualisations dynamically.

The immediate goal is to create a maintainable, secure, extensible analytics platform. Start with a reliable integration and a working MVP before adding advanced statistical features.

## 2. Existing environment

The user is developing and testing locally on a **Mac with Apple Silicon (M1 Pro)**.

Survey Solutions HQ is already running successfully in Docker on that Mac. The Android **Survey Solutions Interviewer** app downloaded from Google Play has successfully connected to HQ.

Known local details:

- HQ local URL on the Mac: `http://localhost:8080`
- Mac LAN IP observed during setup: `10.90.1.42`
- Android devices on the same LAN can reach HQ at `http://10.90.1.42:8080` (subject to the Mac IP and network remaining unchanged).
- Docker Compose project directory used for HQ: `~/Documents/survey-solutions`
- HQ container name used: `survey-hq`
- PostgreSQL container name used: `survey-db`
- HQ image used in the initial setup: `surveysolutions/surveysolutions:latest`
- The Mac requires `linux/amd64` emulation for the HQ image if the image does not provide a native ARM64 variant.

Important:
- `10.90.1.42` is a LAN address and may change. Do not hard-code it into application code.
- `localhost` inside a Docker container refers to that container, not the Mac or another container.
- If the analytics backend runs in the same Docker network as HQ, use the HQ service/container DNS name and internal port, e.g. `http://hq:80`, only after confirming both services share a network and the actual Compose service name.
- If the backend runs directly on macOS, use `http://localhost:8080`.
- The local HTTP setup is for development only. Production must use HTTPS and proper secrets management.
- Never expose PostgreSQL publicly.

## 3. Proposed product capabilities

### 3.1 Multi-questionnaire discovery

- Discover all questionnaires available to the configured HQ API account.
- Display questionnaire title, ID, version, state/status, and relevant metadata when the API provides them.
- Support multiple questionnaire versions without mixing incompatible metadata or variables.
- Allow the user to select a questionnaire and version for analysis.
- Handle questionnaires that have no collected interviews yet.
- Do not assume every questionnaire has the same questions, variable names, sections, rosters, or answer types.

### 3.2 Fieldwork monitoring

Provide indicators where supported by the HQ API, for example:

- Total interviews.
- Interviews by status (started, completed, approved, rejected, etc.).
- Progress against assignments/targets, when assignment data makes the denominator available.
- Interviews by interviewer and supervisor.
- Collection trend by day/week/month.
- Interviews by geography, if valid geographic variables or coordinates are available.
- Last successful synchronisation time and data freshness.

Be precise about definitions. Do not label a count as “completed” or calculate a completion rate unless the relevant status and denominator are confirmed from the API/data model.

### 3.3 Dynamic question-level analytics

Inspect questionnaire metadata and question types, then offer suitable analysis when data permits:

| Data/question type | Possible analysis |
|---|---|
| Single-select categorical | Counts, percentages, bar/pie charts |
| Multi-select | Selection counts and percentages; clarify denominator |
| Numeric | Count, missing count, min, max, mean, median, distribution |
| Date/time | Time series, date distribution |
| Text | Searchable response table; avoid pretending free text has meaningful numeric statistics |
| GPS/geospatial | Map and geographic aggregation where coordinates are available |
| Roster | Roster/member-level analysis with correct parent-child semantics |

Also consider:
- Missing/non-response rates.
- Filters by questionnaire version, interview status, period, interviewer, supervisor, and geographic dimensions when available.
- Cross-tabulations/pivot tables.
- Export of filtered results to CSV/Excel and report formats as a later feature.

Do not infer business meaning from variable names alone. For example, a variable should not be called “household income” unless metadata or a user-defined label confirms that interpretation.

### 3.4 User experience

Suggested main areas:

1. Global overview.
2. Questionnaire catalogue.
3. Questionnaire dashboard.
4. Fieldwork and interviewer monitoring.
5. Question explorer / dynamic analytics.
6. Cross-tabulation builder.
7. Maps (only where valid geographic data exists).
8. Exports and reports.
9. Administration: HQ connection, sync status, application users and permissions.

The dashboard must show real data from the connected HQ. Never ship illustrative/demo numbers as if they were live metrics. If sample data is used in development, clearly label it as demo data.

## 4. Suggested technical architecture

The following is a proposed baseline, not a requirement to introduce unnecessary complexity. Inspect the existing repository first and preserve any valid existing conventions.

### Frontend
- Angular
- Tailwind CSS
- Apache ECharts (or another well-supported charting library if the repository already uses one)
- Responsive desktop/tablet layout

### Backend
- Java + Spring Boot (prefer the version already used by the repository; Spring Boot 3.x is a reasonable starting point)
- REST API for the Angular frontend
- Dedicated Survey Solutions API client/adapter
- Background synchronisation jobs
- Server-side validation, access control, logging, and error handling

### Analytics storage
- PostgreSQL
- Store synchronisation state and metadata locally.
- Decide deliberately whether to store raw interview data, selected analytical projections, or only cached aggregates. Document the choice.
- Avoid duplicating sensitive personal data unnecessarily.
- Make synchronisation idempotent and safe to retry.

### High-level structure

```text
survey-analytics/
├── backend/
│   └── src/main/java/...
│       ├── config/
│       ├── security/
│       ├── surveysolutions/
│       │   ├── client/
│       │   ├── dto/
│       │   └── mapper/
│       ├── questionnaires/
│       ├── interviews/
│       ├── assignments/
│       ├── analytics/
│       ├── synchronization/
│       └── users/
├── frontend/
│   └── src/app/
│       ├── core/
│       ├── shared/
│       ├── features/
│       │   ├── overview/
│       │   ├── questionnaires/
│       │   ├── fieldwork/
│       │   ├── analytics/
│       │   ├── cross-tabulation/
│       │   ├── maps/
│       │   └── administration/
│       └── ...
├── docker-compose.yml
└── CONTEXT.md
```

Adapt this structure to the repository rather than moving or rewriting existing code without a reason.

## 5. Survey Solutions API integration — critical requirements

The HQ API is the source of truth. Before implementing endpoints:

1. Inspect the exact Survey Solutions version running in the user's Docker container.
2. Open the local API documentation/Swagger UI, if enabled:
   `http://localhost:8080/apidocs/index.html`
3. Inspect the actual OpenAPI specification and endpoint schemas for this running version.
4. Confirm authentication method, required roles/permissions, pagination, filtering, response formats, and rate/size limits.
5. Implement only endpoints confirmed by that specification.

Potential API areas to investigate include:
- Questionnaire catalogue and questionnaire versions.
- Statistics endpoints (questionnaire/question metadata and per-question statistics).
- Interview listing and interview details.
- Assignment listing/details.
- Export endpoints for analytical microdata.
- Supervisor/interviewer metadata where accessible.

**Do not assume endpoint paths, query parameter names, response fields, or authentication behaviour from this context file.** Earlier discussion mentioned possible routes such as `/api/v1/questionnaires`, `/api/v1/statistics/questionnaires`, `/api/v1/statistics/questions`, `/api/v1/statistics`, `/api/v1/interviews`, `/api/v1/assignments`, `/api/v1/supervisors`, and `/api/v2/export`. Treat these strictly as investigation leads, not verified contracts. Confirm each one against the local OpenAPI specification before coding.

Authentication:
- Create/use a dedicated Survey Solutions **API User** account in HQ if required by the installed version.
- Do not use an Interviewer account for backend integration.
- Keep HQ credentials/tokens on the backend only.
- Never expose secrets in Angular, browser storage, source control, logs, screenshots, or API responses.
- Prefer environment variables or a local untracked `.env` file for development; provide `.env.example` with placeholders.
- Verify whether Basic authentication or token/JWT authentication is supported and configured in the installed version. Do not assume Bearer/JWT is enabled by default.

## 6. Synchronisation design

Implement a robust synchronisation mechanism:

- Initial full discovery of questionnaires and versions.
- Initial data load for selected questionnaires.
- Incremental updates where the API supports reliable modified-since/cursor/pagination semantics.
- Scheduled synchronisation configurable by environment/property (initial desired interval: every 5 minutes).
- Manual “Synchronise now” action.
- Per-questionnaire sync status: last attempt, last success, records fetched, duration, and error.
- Retry transient failures with bounded backoff.
- Idempotent upserts; prevent duplicate interviews.
- Handle deletions, status transitions, and changed interviews according to the API's actual semantics.
- Respect API pagination and do not silently truncate large datasets.
- Keep timestamps in UTC in storage; format for the user's timezone in the UI.
- Make data freshness visible to users.

Do not run expensive full exports every five minutes without evaluating data volume and HQ/API impact. Choose a suitable strategy after examining the available API.

## 7. Analytics correctness and privacy

- Clearly distinguish HQ-provided statistics from statistics calculated by this platform.
- Define every KPI and its numerator/denominator.
- Handle missing values, “don't know”, “refused”, invalid values, multi-select answers, and roster data correctly.
- Do not average categorical codes as if they were numeric measurements.
- Respect questionnaire version differences.
- Avoid double-counting interviews or roster members.
- Use least-privilege access.
- Treat survey data as potentially sensitive personal data.
- Do not expose respondent identifiers or personal answers to users who are not authorised.
- Add audit logging for sensitive administrative actions.
- Provide configurable retention and deletion policies if raw data is persisted.

## 8. Local development and Docker

The user already has HQ and PostgreSQL running in a separate Compose project under `~/Documents/survey-solutions`.

When creating the analytics project's Compose file:
- Do not accidentally create conflicting container names or ports.
- Do not recreate or delete the existing HQ database.
- Do not use `docker compose down -v` on the existing HQ project.
- Prefer a shared external Docker network or a clearly documented connection approach if the backend container must call HQ.
- Ensure PostgreSQL data is persisted.
- Keep credentials in environment variables.
- Add health checks and sensible restart policies.
- Document how to start/stop the analytics stack independently from HQ.

For local macOS development, it is acceptable to run Angular and Spring Boot outside Docker initially if that makes debugging easier. The backend can call HQ through `http://localhost:8080`.

## 9. Implementation approach for GitHub Copilot

Work incrementally. Do not generate the entire platform in one unreviewable change.

### Phase 0 — Repository reconnaissance
- Inspect repository structure, current code, README, build files, dependencies, and existing conventions.
- Identify whether frontend/backend already exist.
- Report the current state and propose the smallest safe implementation plan.
- Do not overwrite existing work.

### Phase 1 — Verify HQ integration
- Identify HQ version and inspect local OpenAPI documentation.
- Test API authentication with a minimal backend/client request.
- Confirm questionnaire discovery and one read-only endpoint.
- Add integration tests or recorded fixtures (without real personal data).
- Document verified endpoints and example response schemas.

### Phase 2 — Backend foundation
- Configure HQ connection securely.
- Implement typed API client, DTOs, error handling, timeouts, and pagination.
- Expose internal endpoints for questionnaire discovery and sync status.
- Add health/readiness endpoints.
- Add tests.

### Phase 3 — Questionnaire catalogue and fieldwork KPIs
- Build questionnaire list and details.
- Implement validated interview/assignment metrics.
- Show data freshness and sync errors.

### Phase 4 — Dynamic question analytics
- Parse questionnaire metadata.
- Map question types to analytical strategies.
- Build safe aggregations and question explorer.
- Handle versions, rosters, missing values, and multi-select questions.

### Phase 5 — Advanced analytics
- Cross-tabulation/pivot builder.
- Geographic visualisation.
- Exports and scheduled reports.

### Phase 6 — Production readiness
- Roles and permissions.
- HTTPS/reverse proxy.
- Backups, monitoring, logging, migrations.
- Performance testing and data-protection review.

At the end of each phase:
- Run relevant tests and builds.
- Summarise files changed and decisions made.
- List remaining issues and next steps.
- Do not claim a feature works unless it has been run and verified.

## 10. Instructions for GitHub Copilot

Act as a senior full-stack engineer and technical architect.

- First inspect the existing project and this `CONTEXT.md`.
- Ask a concise clarification only when a genuinely blocking requirement is missing.
- Prefer small, production-quality changes over large speculative scaffolding.
- Do not invent Survey Solutions API endpoints or response schemas. Verify them from the running instance's OpenAPI/Swagger documentation.
- Do not assume the API provides every raw answer or every desired statistic. Determine whether data must be retrieved through statistics endpoints, exports, or another documented mechanism.
- Keep Survey Solutions integration behind a dedicated adapter/client so API changes do not leak through the application.
- Use typed DTOs and explicit mapping; avoid untyped `Map<String,Object>` except where questionnaire answers are inherently dynamic, and isolate that dynamic handling.
- Validate all user inputs and protect against injection, broken access control, and data leakage.
- Never put API credentials in frontend code.
- Add tests for API client behaviour, pagination, aggregation, questionnaire version handling, and error cases.
- Keep documentation up to date.
- Do not add dependencies unless they provide clear value.
- Preserve existing repository architecture and naming conventions when reasonable.
- Do not commit secrets, real respondent data, or local database files.

## 11. First task to give Copilot

Start with **Phase 0 and Phase 1 only**.

Inspect the repository and `CONTEXT.md`. Then:
1. Summarise the current project structure and existing implementation.
2. Identify the exact Survey Solutions HQ version and how to access its local OpenAPI specification.
3. Verify the API authentication mechanism and the exact endpoints/schemas needed to list questionnaires and retrieve statistics/interview data.
4. Propose a minimal integration proof of concept.
5. Do not implement the full dashboard until the API contract has been verified.

## 12. Known facts versus decisions still to make

Known:
- HQ runs locally in Docker on the user's Mac.
- Android Interviewer successfully connected to HQ.
- The user wants a web analytics platform that can work with multiple questionnaires.
- The proposed stack is Angular, Spring Boot, PostgreSQL, and ECharts.

Still to decide/verify:
- Exact HQ version and API contract.
- Whether the local API docs are enabled and accessible.
- Which API authentication method is configured.
- Whether analytics should cover every questionnaire automatically or only selected ones.
- Whether raw microdata should be persisted locally.
- User roles and access model.
- Final KPI definitions and reporting requirements.
- Production hosting/domain/HTTPS.

---
**Project working name:** Survey Solutions Analytics  
**Primary objective:** A reusable, secure, multi-questionnaire analytics and fieldwork monitoring platform powered by the official Survey Solutions Headquarters API.


