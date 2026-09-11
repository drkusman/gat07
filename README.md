# Grassroot Advocacy for Tinubu (GAT) 2027 — Spring Boot edition

**Motto: Forward Together with PBAT**

Member registration, referral and field-reporting platform, down to every polling unit in Nigeria.

- **Java 21 · Spring Boot 4** — Spring MVC + Thymeleaf (server-rendered pages), Spring Security (form login by phone, BCrypt, remember-me), Spring Data JPA, Flyway migrations, Bean Validation, Actuator
- **PostgreSQL** in production (Railway) — **H2 in PostgreSQL mode** as a zero-config local database (file `data/gat.mv.db`)
- **Leaflet** maps for polling-unit GPS capture and coverage
- Deploys to **Railway** from git with a Dockerfile; migrations, seed data and the polling-unit import run automatically at start
- **`web/`** — a separate **Next.js** app for the public home page and events list, reading a public JSON API from this
  backend. Registration, login, the member dashboard and the coordination centre stay on this Spring Boot app. See
  [`web/README.md`](web/README.md).

## Run locally

Requires JDK 21 (Maven is downloaded by the wrapper).

```bash
./mvnw spring-boot:run          # Windows: mvnw.cmd spring-boot:run
```

Then open <http://localhost:8080>. On the first start the app creates the schema, seeds the 6 zones / 37 states / 774 LGAs and the
default admin, and imports the 174,427 polling units from `src/main/resources/data/polling-units.csv` (about 20 seconds).

To use your local PostgreSQL instead of H2, set `DATABASE_URL` before starting (create the database first):

```
DATABASE_URL=postgresql://postgres:YOUR_PASSWORD@localhost:5432/gat2027
```

**Default admin:** phone `08000000000`, password `admin1234` — change it after the first login (Profile & Settings), or set
`GAT_ADMIN_PHONE` / `GAT_ADMIN_PASSWORD` before the first start.

| Page | URL | Who |
|---|---|---|
| Home | `/` | Everyone |
| Registration | `/register` or `/register?ref=GAT-XXXXXX` (referral link) | Public |
| Login | `/login` | Members |
| Member dashboard | `/dashboard` (overview, referrals, polling unit, reports, profile) | Members |
| File a field report | `/report` | Members |
| Coordination centre | `/admin` (overview, breakdown, map, leaderboard, members, reports, **events**, location data, settings) | Admin · state coordinators |
| User manual (PDF) | `/docs/GAT-2027-User-Manual.pdf` — linked from both dashboards | Everyone |
| Public events API | `GET /api/events` — published events as JSON, used by `web/` | Everyone |
| Health check | `/actuator/health` | Railway |

Events (title, date, location, description, cover image, video and photo-gallery links) are managed at
**Admin → Events** (admin role only) and shown on the public home page (`web/`) once published.

## Deploy on Railway (git)

1. Push this repository to GitHub and create a Railway project from it (Railway detects the `Dockerfile`).
2. Add the **PostgreSQL** plugin to the project.
3. On the web service set the variables:
   - `DATABASE_URL` = `${{Postgres.DATABASE_URL}}`
   - `THYMELEAF_CACHE` = `true`
   - `GAT_ADMIN_PHONE`, `GAT_ADMIN_PASSWORD` (optional, first run only)
   - `GAT_REMEMBER_KEY` = any long random string (keeps "remember me" logins valid across restarts)
4. Deploy. Flyway applies migrations, the seed runs, and the polling units are imported on the first start.

Every later push redeploys; pending migrations are applied automatically.

## Location data

States and LGAs are built in (`seed/NigeriaData.java`). Wards and polling units come from the CSV and are imported when the
`polling_units` table is empty. To add or update data later use **Admin → Location Data → Import from CSV**, or POST a CSV body to
`/api/admin/import` as an admin. Accepted columns (any order, flexible names): `state`, `lga`, `ward`, `polling_unit`/`name`,
`pu_code`, `latitude`, `longitude`. Rows are matched by name so re-imports update instead of duplicating; coordinates outside
Nigeria are dropped. Members can also type a ward or polling unit that is missing during registration.

## Age limit

Administrators set the registration age rules under **Admin → Settings & Age Limit**: minimum age (default 16), optional maximum
age, and whether date of birth is mandatory. Enforced server-side at registration and on profile updates; the forms show the
limit and restrict the date picker.

## Roles

- **MEMBER** — register, edit profile, share referral link, file reports.
- **COORDINATOR** — member plus the coordination centre limited to their own state.
- **ADMIN** — everything, all states, role/status management, password resets, CSV import, settings.

Assign roles from **Admin → Members → View**.

## Project layout

```
src/main/java/ng/gat2027/grassroot/
  config/        DataSourceConfig (DATABASE_URL → JDBC)
  domain/        JPA entities and enums
  repo/          Spring Data repositories
  security/      SecurityConfig, MemberUserDetailsService, MemberPrincipal, CurrentUser
  service/       MemberService, LocationService, ReportService, SettingsService, AnalyticsService (JDBC read models), CsvImportService, SeedService
  seed/          NigeriaData (zones, states, LGAs)
  web/           MVC controllers (Home, Auth, Dashboard, Report, Admin) and form objects
  api/           JSON / CSV endpoints (location lookups, referral check, map data, export, import)
src/main/resources/
  application.yml, db/migration/V1__init.sql
  templates/     Thymeleaf pages (fragments/, dashboard/, admin/)
  static/        css, js/gat.js, img, docs/GAT-2027-User-Manual.pdf
  data/          polling-units.csv
```

## Useful commands

| Command | Purpose |
|---|---|
| `./mvnw spring-boot:run` | run in development |
| `./mvnw -DskipTests package` | build `target/grassroot-0.0.1-SNAPSHOT.jar` |
| `java -jar target/grassroot-0.0.1-SNAPSHOT.jar` | run the built jar |
| `GAT_IMPORT_ON_STARTUP=false` | skip the automatic CSV import |

Delete `data/` to reset the local H2 database.
