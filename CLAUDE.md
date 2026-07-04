# BGRemover — Project Rules (CLAUDE.md)

Local web application for Canva-quality background removal from images.
**Hybrid architecture:** Spring Boot (Java) main backend + thin Python FastAPI
inference microservice for AI models + React/TypeScript frontend.
Everything runs 100% locally — no paid APIs, open-source models only.

Source of truth for requirements: `C:\Users\HP\Downloads\Prompt_For_BG_Removal_Website.txt`
(full version, 78 lines — an earlier truncated copy caused the initial build to
miss features; if requirements seem missing, re-read that file).

## Environment & installation rules (check BEFORE any install or download)

- **This is a Windows 11 machine. ALL development tooling lives on the E: drive.
  NEVER install anything to C:.**
- Before installing ANYTHING, first check whether it already exists on E:
  (`where <tool>`, PATH, E:\ tool directories) and reuse it. **Ask the user
  before installing anything new.**
- Every new installation, dependency cache, model weight, temp file, and build
  artifact MUST go to the E: drive.
- If a tool genuinely cannot be redirected off C:, STOP and surface the
  tradeoff instead of proceeding silently.
- Docker (if used): image storage location is a Docker Desktop setting — flag
  it to the user rather than assuming. (Docker is currently NOT installed;
  docker-compose.yml is provided but untested locally.)
- App temp/output/upload directories: under `E:\Projects\BGRemover\data\`
  (git-ignored), never the system temp on C:. Spring multipart temp is pinned
  there via `spring.servlet.multipart.location`.
- The E:-drive policy is encoded in the checked-in start scripts
  (`start-*.cmd`), `.env.example`, and Maven/pip global config so it survives
  new sessions. Every env var is documented in `.env.example` and README.

### Verified tool locations (checked 2026-07-03)

| Tool | Location | Version |
|---|---|---|
| Node.js | `E:\NodeJS` | v22.16.0 |
| Python | `E:\Python313` | 3.13.1 |
| Git | `E:\Git` | 2.49.0.windows.1 |
| Java/JDK | `E:\Java\jdk-21.0.11+10` (Temurin, installed 2026-07-03 with user approval) | 21.0.11 LTS |
| Maven | `E:\Maven\apache-maven-3.9.16` (localRepository → `E:\maven-repo` in its `conf\settings.xml`) | 3.9.16 |
| Ollama | `E:\Ollama` (models: `E:\OllamaModels`) | — |
| npm cache | `E:\npm-cache` (via `npm config get cache`) | — |
| pip cache | `E:\pip-cache` (via pip config + `PIP_CACHE_DIR`) | — |
| gh CLI | **NOT INSTALLED** (verified 2026-07-03 — prompt assumed it exists; needs user approval to install to E:) | — |
| Docker | **NOT INSTALLED** | — |

JDK and Maven are NOT on PATH — start scripts and any shell commands must set
`JAVA_HOME=E:\Java\jdk-21.0.11+10` and call `E:\Maven\apache-maven-3.9.16\bin\mvn.cmd`.
`python` on PATH resolves to `E:\Python313\python.exe`; ignore the Windows
Store stub on C:.

### Storage configuration

- **Project repo:** `E:\Projects\BGRemover`
- **Python:** venv at `inference\.venv` (created from `E:\Python313\python.exe`);
  `PIP_CACHE_DIR=E:\pip-cache`
- **Node:** npm cache already `E:\npm-cache` — do not change
- **Maven:** local repo `E:\maven-repo` via `E:\Maven\apache-maven-3.9.16\conf\settings.xml`
- **AI model weights** (set by start scripts and `app/config.py` defaults):
  `U2NET_HOME=E:\AIModels\u2net` (all rembg + SAM weights land here),
  `HF_HOME=E:\AIModels\huggingface`, `TORCH_HOME=E:\AIModels\torch`

## Architecture (three components, each independently runnable)

```
frontend/   React + TypeScript (strict) + Vite. Canvas-based selection
            (points / box / brush) and mask overlay. Talks ONLY to Spring Boot
            through one typed API client module (src/api.ts).
backend/    Spring Boot 3.5 (Java 21). The ONLY service the frontend talks to.
            Owns upload/validation, temp-file management, orchestration,
            export, API contract (OpenAPI via springdoc at /swagger-ui.html),
            error handling, proxying to inference. NO ML logic in Java.
inference/  Python 3.13 FastAPI, internal-only (127.0.0.1:8000). Owns ONLY
            model loading + inference: image (+ optional prompt points/boxes)
            in, mask or cutout out. Stateless per-request (video-ready).
            No business logic. OpenAPI at /docs.
```

- Java ↔ Python boundary is treated as a public API: explicit multipart/JSON
  contracts, validated on both sides (Bean Validation / Pydantic), documented
  by each service's OpenAPI. Breaking changes require bumping both sides in
  one PR.
- Spring Boot degrades gracefully when inference is down: startup health poll
  logs status; requests return 503 with a clear message the UI displays.
- Design decision: business/orchestration lives in Java (team strength);
  ML runtime lives in Python (ecosystem strength). Any new AI capability goes
  into `inference/` behind the same contract style.
- Video (future): inference endpoints stay stateless and per-frame with I/O
  separated from processing so a video pipeline can batch frames through them.
  Do not build video now.

## Model strategy

- `inference/app/registry.py` defines the `BackgroundRemover` interface
  (`remove(image, **opts) -> RGBA image` / `mask(image, **opts) -> L mask`).
  Every model implements it; nothing outside the registry imports a specific
  model.
- Models are **runtime-configurable — switching never requires a rebuild**:
  `inference/config.yaml` declares the registry; `GET /models` lists them;
  `PUT /models/active` switches the default at runtime. Spring proxies these
  at `/api/models` (+ `/api/models/active`); the UI shows a dropdown.
- Lazy loading: weights download to `E:\AIModels` on first use (LRU cache of
  loaded sessions, size in config.yaml).
- Registered models: u2net (fast default preview), u2netp, isnet-general-use
  (better edges/hair), isnet-anime, birefnet-general (highest quality; GPU
  recommended, slow on CPU), birefnet-general-lite, birefnet-portrait, and
  `sam` for interactive selection.
- Known deviations (flagged, not silent): rembg ships SAM vit_b — MobileSAM /
  SAM2 are not available in rembg 2.0.76 and would need a custom ONNX
  integration (tracked as future work). BEN2 requires manually-supplied
  weights (`ben_custom`) and InSPyReNet has no rembg session; both skipped.
  bria-rmbg excluded (non-commercial license).

## GitHub workflow (mandatory for ALL work)

- GitHub is the single source of truth. Repo setup + initial scaffold push
  happen before feature development. Use the `gh` CLI (currently NOT installed
  — get user approval, install to E:, e.g. `E:\GitHubCLI`).
- Every new feature: GitHub Issue (with acceptance criteria) →
  branch `feature/<issue-number>-<short-name>` from main → conventional
  commits (`feat:`, `fix:`, `refactor:`, `test:`, `docs:`) referencing the
  issue → PR → merge only after the checklist passes (formatted, linted,
  tested, manually verified). **Never commit directly to main** (after the
  initial scaffold).
- Every bug: Issue labeled `bug` with repro steps → `fix/<issue-number>-<name>`
  → PR → merge.
- Small atomic commits, one logical change each. Close issues via PR
  descriptions ("Closes #12").
- Before starting any coding task, state which issue/branch it is under;
  create one first if none exists.

## Coding standards

**Java (backend/):**
- Java 21, Spring Boot 3.x. Layers: controller → service → client.
  Controllers thin; business logic in services; the inference call isolated in
  `InferenceClient` (interface) + `RestInferenceClient` (impl) so it can be
  mocked.
- Records/DTOs for API contracts; Bean Validation on inputs; global
  `@RestControllerAdvice` maps domain exceptions to HTTP statuses. No business
  logic in controllers. Constructor injection only.
- Tests: JUnit 5 + Mockito (services), `@WebMvcTest` (controllers), and at
  least one WireMock-backed integration test stubbing inference.
- Spotless (google-java-format) + Checkstyle (Google style). Run
  `mvn spotless:apply` before committing; `mvn verify` must pass.

**Python (inference/):**
- Type hints everywhere; Pydantic schemas for request/response bodies; ruff
  for format + lint (`ruff format`, `ruff check`); pytest with real small
  sample images (generated in tests); specific exceptions, no bare `except`;
  Google-style docstrings.

**TypeScript (frontend/):**
- Strict mode; no `any` without a justifying comment; functional components +
  hooks only; ESLint + Prettier; ALL backend calls through `src/api.ts`.

**All:**
- Small single-purpose functions; comments explain why, not what; no dead code.
- Before implementing any non-trivial feature, state the plan (files touched,
  interfaces) and wait for user confirmation.
- If a request conflicts with these rules, flag the tradeoff instead of
  silently violating them.

## Build / run / test commands

| Task | Command |
|---|---|
| Whole stack | `start-all.cmd` → app at http://localhost:8080 |
| Inference only | `start-inference.cmd` (uvicorn on :8000) |
| Backend only | `start-backend.cmd` (Spring Boot on :8080) |
| Frontend dev | `cd frontend && npm run dev` (Vite :5173, proxies /api → :8080) |
| Frontend build | `cd frontend && npm run build` (output served by Spring Boot) |
| Backend tests | `mvn.cmd -f backend/pom.xml verify` (JAVA_HOME must point to E: JDK) |
| Inference tests | `inference\.venv\Scripts\python.exe -m pytest inference/tests` |
| Lint Java | `mvn.cmd -f backend/pom.xml spotless:apply checkstyle:check` |
| Lint Python | `inference\.venv\Scripts\ruff.exe check inference/app` |
| Lint/typecheck TS | `cd frontend && npm run lint && npm run typecheck` |
| Docker | `docker compose up` (untested — Docker not installed on this machine) |

## Current status / next steps (keep updated every session)

**Session paused 2026-07-04 mid-way through implementing the full prompt.**

**Done:**
- JDK 21 + Maven installed to E:; all caches/weights on E:.
- Working app (verified E2E): auto removal (7 rembg models), SAM point + box
  selection, client-side brush refine (restore/erase/undo), before/after
  slider, PNG export. Run with `start-all.cmd` → http://localhost:8080.
- **inference/ fully upgraded to prompt spec** (renamed from ml-service/,
  venv recreated): `BackgroundRemover` ABC + `ModelRegistry` (registry.py),
  `config.yaml`, Pydantic schemas, `PUT /models/active`, `invert` param
  ("remove what I selected"), type hints + docstrings, ruff clean,
  **pytest 10/10 green** (`inference\.venv\Scripts\python.exe -m pytest tests`).
- CLAUDE.md rewritten with all prompt-mandated sections.
- Start scripts updated (`start-inference.cmd` replaces `start-ml-service.cmd`;
  the old file may still exist — it is obsolete, do not use).

**NOT done — resume here, in order:**
1. **Backend restructure** (was next in progress, no new code written yet):
   controller → service → client layers (`InferenceClient` interface +
   `RestInferenceClient` impl), DTOs + Bean Validation, global
   `@RestControllerAdvice`, proxy `PUT /api/models/active`, forward the new
   `invert` field, startup health poll of inference, multipart temp →
   `E:\Projects\BGRemover\data\tmp`, springdoc OpenAPI, Spotless + Checkstyle,
   tests (Mockito service test, `@WebMvcTest`, WireMock integration test).
   Old code still lives at `com/bgremover/web/BackgroundRemovalController.java`
   + `config/MlServiceConfig.java` with property prefix `bgremover.ml-service`
   in application.yml — replace these. (Old backend still works against the
   new inference service; `invert` just isn't forwarded yet.)
2. **Frontend**: convert to TypeScript strict (tsconfig, `.tsx`, typed
   `src/api.ts` client), ESLint + Prettier, brush-select tool (stroke sampled
   into SAM points), keep/remove toggle (sends `invert=true`). Konva.js was
   deliberately not used — native canvas covers point/box/brush; flag if user
   wants konva anyway.
3. **docker-compose.yml + Dockerfiles + `.env.example`**, README update.
4. Full verify: `mvn verify`, pytest, `npm run build`, E2E smoke test.
5. **Git/GitHub**: repo is `git init`-ed, ZERO commits yet. Make initial
   scaffold commit, then: gh CLI is NOT installed — **ask user approval** to
   install to E:, `gh auth login` by user, create repo, push, then strict
   issue/branch/PR flow for all further work.

**Future (after the above):** MobileSAM/SAM2 ONNX integration; GPU execution
provider for BiRefNet; video pipeline (Phase 2).
