# BGRemover

Local web app for removing image backgrounds (Canva-quality), 100% offline
after the first model download. No paid APIs, open-source models only.

## Run

```
start-all.cmd        # both services; app at http://localhost:8080
```

or individually: `start-inference.cmd` (FastAPI :8000) and `start-backend.cmd`
(Spring Boot :8080). Docker alternative: `docker compose up` (see the E:-drive
note inside docker-compose.yml).

First use of a model downloads its weights to `E:\AIModels` (~4–930 MB
depending on model). Later runs are fast.

## Features

- **Auto mode** — one click removes the background with the selected model.
- **Select object mode** (SAM) — point clicks (left = keep, right = exclude),
  drag a box, or **brush** over an object; choose **Keep selection** or
  **Remove selection**.
- **Refine with brush** — after any result: restore/erase areas locally with
  undo, entirely in the browser.
- Before/after comparison slider, transparent-PNG export at original
  resolution, runtime model switching (no rebuild).

## Architecture

```
frontend/   React + TypeScript (strict) + Vite. All backend calls through the
            typed client src/api.ts. Dev server :5173 proxies /api to :8080.
backend/    Spring Boot 3.5 / Java 21 — the only service the frontend talks
            to. Layers: controller → service → InferenceClient. OpenAPI UI at
            http://localhost:8080/swagger-ui.html
inference/  Python 3.13 FastAPI (localhost-only) — model registry + inference,
            stateless per request (video-ready). OpenAPI at
            http://127.0.0.1:8000/docs
```

### API (frontend ↔ backend)

| Endpoint | Purpose |
|---|---|
| `GET /api/models` | model registry + active model |
| `PUT /api/models/active` `{"name": "..."}` | switch active model at runtime |
| `GET /api/health` | backend + inference health |
| `POST /api/remove` | multipart: `file`, `model?`, `alphaMatting?`, `points?` (JSON array of `{x,y,label}` and/or `{x1,y1,x2,y2}`), `invert?` → transparent PNG |

Models: u2net, u2netp, isnet-general-use, isnet-anime, birefnet-general,
birefnet-general-lite, birefnet-portrait, sam (interactive). Registry lives in
[inference/config.yaml](inference/config.yaml).

## Development

| Task | Command |
|---|---|
| Backend tests + lint | `mvn.cmd -f backend/pom.xml verify` (Spotless + Checkstyle run in verify) |
| Inference tests | `inference\.venv\Scripts\python.exe -m pytest inference/tests` |
| Inference lint | `inference\.venv\Scripts\ruff.exe check inference/app` |
| Frontend | `npm run typecheck && npm run lint && npm run build` in `frontend/` |

Toolchain paths, E:-drive policy, coding standards, and the GitHub workflow
are all in [CLAUDE.md](CLAUDE.md). Environment variables: [.env.example](.env.example).
