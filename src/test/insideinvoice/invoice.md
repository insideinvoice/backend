# Inside Invoice — Backend Deployment Guide

Deploy the Spring Boot jar against **any empty PostgreSQL** and it comes up fully
provisioned: Flyway (V1–V25) creates every table, then seeds two default accounts.
No manual SQL, no manual schema work.

---

## 1. Required environment variables

The jar reads standard Spring properties from env vars. Only the datasource is
strictly required — everything else has working defaults baked into
`application.yml`.

| Env var | Required | Example | Notes |
|---|---|---|---|
| `SPRING_DATASOURCE_URL` | **Yes** | `jdbc:postgresql://host:5432/postgres?sslmode=require` | Must start with `jdbc:postgresql://` (not `postgres://`). Add `?sslmode=require` for Neon/RDS. |
| `SPRING_DATASOURCE_USERNAME` | **Yes** | `postgres` | DB user. |
| `SPRING_DATASOURCE_PASSWORD` | **Yes** | `your-db-password` | DB password. |
| `SERVER_PORT` | No | `8080` | Container platforms (Railway, Render, Cloud Run) inject their own port — pass it through: `SERVER_PORT=$PORT`. |
| `SPRING_PROFILES_ACTIVE` | No | `prod` | Default is already `prod`. Leave it. |
| `APP_BASE_URL` | Recommended | `https://api.yourapp.com` | Public API origin used in share links / emails. Default: `https://backend-production-1509.up.railway.app`. |
| `APP_FRONTEND_BASE_URL` | Recommended | `https://yourapp.com` | Frontend origin for CORS + invoice share links. Default: `https://insideinvoice.in`. |
| `BACKUP_ENABLED` | No | `false` | `true` (default) runs scheduled `pg_dump` backups to Google Drive. Set `false` if you don't have the Google Drive creds configured — the app runs fine without them. |

JWT secret, mail (Resend) API key, and backup encryption keys have defaults in
`application.yml`. Override via env only if you rotate them:
`APP_JWT_SECRET`, `APP_MAIL_RESEND_API_KEY`, `BACKUP_ENCRYPTION_KEY`, etc.
(Spring relaxed binding: `app.jwt.secret` → `APP_JWT_SECRET`.)

### What gets auto-created on first boot

1. **Full schema** — all 16 tables via Flyway migrations V1–V25.
2. **Two seed users** (idempotent — skipped if already present):
   - `rshardware2210@gmail.com` / `RSHARDWARE` — USER, business "RS Hardware Glass & Electrical".
   - `insideinvoice` / `invoice6688inside` — ADMIN, business "Inside Invoice Admin".

Health check: `GET /actuator/health` → `{"status":"UP"}`.

---

## 2. Run the jar directly (any VM / EC2 / bare metal)

```bash
# one-time: Java 17+
apt-get install -y openjdk-21-jre-headless   # Debian/Ubuntu
# yum install -y java-21-openjdk-headless    # Amazon Linux / RHEL

SPRING_DATASOURCE_URL='jdbc:postgresql://db-host:5432/postgres?sslmode=require' \
SPRING_DATASOURCE_USERNAME='postgres' \
SPRING_DATASOURCE_PASSWORD='secret' \
SERVER_PORT=8080 \
APP_BASE_URL='https://api.example.com' \
APP_FRONTEND_BASE_URL='https://app.example.com' \
java -jar inside-invoice-1.0.0.jar
```

systemd unit (`/etc/systemd/system/inside-invoice.service`):

```ini
[Unit]
Description=Inside Invoice API
After=network.target

[Service]
User=app
Environment=SPRING_DATASOURCE_URL=jdbc:postgresql://db-host:5432/postgres?sslmode=require
Environment=SPRING_DATASOURCE_USERNAME=postgres
Environment=SPRING_DATASOURCE_PASSWORD=secret
Environment=SERVER_PORT=8080
Environment=APP_BASE_URL=https://api.example.com
Environment=APP_FRONTEND_BASE_URL=https://app.example.com
ExecStart=/usr/bin/java -jar /opt/inside-invoice/inside-invoice-1.0.0.jar
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload && sudo systemctl enable --now inside-invoice
```

---

## 3. Docker

`Dockerfile` (in repo root):

```dockerfile
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/inside-invoice-1.0.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

```bash
mvn -DskipTests package
docker build -t inside-invoice .
docker run -d --name inside-invoice -p 8080:8080 \
  -e SPRING_DATASOURCE_URL='jdbc:postgresql://host.docker.internal:5432/postgres?sslmode=require' \
  -e SPRING_DATASOURCE_USERNAME='postgres' \
  -e SPRING_DATASOURCE_PASSWORD='secret' \
  -e SERVER_PORT=8080 \
  inside-invoice
```

`docker-compose.yml` with a bundled Postgres (good for local/demo):

```yaml
services:
  db:
    image: postgres:16
    environment:
      POSTGRES_DB: insideinvoice
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: secret
    volumes:
      - pgdata:/var/lib/postgresql/data

  api:
    build: .
    ports:
      - "8080:8080"
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://db:5432/insideinvoice
      SPRING_DATASOURCE_USERNAME: postgres
      SPRING_DATASOURCE_PASSWORD: secret
      SERVER_PORT: 8080
      BACKUP_ENABLED: "false"
    depends_on:
      - db

volumes:
  pgdata:
```

```bash
docker compose up -d
```

---

## 4. Railway

1. Push this branch to GitHub.
2. Railway → **New Project → Deploy from GitHub repo** → pick the backend repo,
   branch `release-v3.0.0_09102026`.
3. Add a **PostgreSQL** service in the same project (or use an external Neon/RDS DB).
4. In the API service → **Variables**, set:
   ```
   SPRING_DATASOURCE_URL     = jdbc:postgresql://${{Postgres.PRIVATE_URL}}  # Railway Postgres
   SERVER_PORT               = ${PORT}
   APP_BASE_URL              = https://<your-service>.up.railway.app
   APP_FRONTEND_BASE_URL     = https://insideinvoice.in
   BACKUP_ENABLED            = false
   ```
   For an external DB (e.g. Neon), set the three `SPRING_DATASOURCE_*` vars
   directly with the `jdbc:postgresql://...` URL (convert `postgres://` →
   `jdbc:postgresql://` and drop any `@` encoding issues).
5. Deploy. First boot runs Flyway V1–V25, seeds the two users, and serves traffic.

---

## 5. Other platforms (Elastic Beanstalk / Render / Cloud Run)

Same pattern everywhere: **set the env vars, run the jar**.

- **AWS Elastic Beanstalk (Java platform)**: upload the jar as
  `Dockerrun`/jar bundle, set the env vars in the EB console → Configuration → Software.
- **Render**: New → Web Service → build command `mvn -DskipTests package`,
  start command `java -jar target/inside-invoice-1.0.0.jar`, paste the env vars.
- **Google Cloud Run**: `gcloud run deploy --image ... --set-env-vars SPRING_DATASOURCE_URL=...`
  (Cloud Run injects `PORT`; map it with `SERVER_PORT=$PORT`).

---

## 6. Disaster recovery

If the database is ever lost: provision an **empty** Postgres, point
`SPRING_DATASOURCE_URL` at it, and start this jar. Schema + the two users come
back automatically. Existing production DBs are unaffected — the seed migration
only inserts what's missing.
