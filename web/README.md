# GAT 2027 — public site (Next.js)

The public marketing site: home page, hero, and the events list. Registration, login, the
member dashboard and the coordination centre (admin) all stay on the Spring Boot app in the
parent directory — this app only owns `/` and reads events from its public API.

## Run locally

The Spring Boot backend must be running first (see the parent `README.md`; default `http://localhost:8080`).

```bash
npm install
npm run dev
```

Open <http://localhost:3000>.

## Environment variables

Create `.env.local` (already gitignored):

```
API_BASE=http://localhost:8080
NEXT_PUBLIC_APP_BASE=http://localhost:8080
```

- `API_BASE` — used server-side to fetch `/api/events`. Not exposed to the browser.
- `NEXT_PUBLIC_APP_BASE` — the Spring Boot app's public URL, used for the Register/Login links and
  for image URLs (`/img/...`). Exposed to the browser, so it must be the real public URL in production.

If both apps are deployed on the same domain (e.g. this app at `/` and Spring Boot behind a reverse
proxy at `/app`), set `NEXT_PUBLIC_APP_BASE` to that path/origin accordingly.

## Managing events

Events are created and edited in the Spring Boot admin panel at **Coordination centre → Events**
(admin role required), not in this app. This app only reads published events from `GET /api/events`.
Events without a video or photo link yet show a "Photos & videos coming soon" placeholder — you can
publish an event before the media is ready and add the links later by editing it.
