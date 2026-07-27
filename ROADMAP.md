# Roadmap

Geplante Verbesserungen, sortiert nach empfohlener Reihenfolge (Preis/Nutzen).

## 1. Security-Basis ✅ umgesetzt

- **Rate Limiting** auf Auth-Endpoints (Login, Passwort-Reset, Invitations): Bucket4j-Filter, in-memory pro IP. Redis-Buckets erst bei Multi-Instanz.
- **Login-Lockout**: Failed-Attempt-Counter am `UserModel`, Account temporär sperren nach N Fehlversuchen.
- **User-Enumeration-Fix**: Login/Reset/Invite liefern identische Antworten, egal ob User existiert ("user not found" vs. "wrong password" nicht unterscheidbar).
- **Security Headers**: CSP, X-Content-Type-Options, Referrer-Policy, HSTS (sobald TLS) via Spring Security.

## 2. Docker Compose + Backups

- Compose-Stack: Postgres, Mailhog, Backend, Frontend.
- Enabler für alle weiteren Bausteine (RabbitMQ, Redis = jeweils wenige Zeilen YAML).
- DB-Backups: pg_dump-Cronjob. Einziger Schutz gegen echten Datenverlust.

## 3. RabbitMQ für Mailversand ✅ umgesetzt

- Event statt Sync-Call: `project.invitation.created` etc. → Queue → Mail-Consumer.
- Retry mit Backoff, danach DLQ. Consumer idempotent bauen (at-least-once = Duplikate möglich).
- Publish erst nach DB-Commit: `@TransactionalEventListener(AFTER_COMMIT)`.
- Topic-Exchange von Anfang an. Spätere Konsumenten: In-App-Notifications, Matching-Neuberechnung, Audit-Events.

## 4. Caching ✅ umgesetzt

- Spring Cache + Caffeine (in-memory, eine Instanz → kein Netzwerk-Hop, keine Infra).
- `@Cacheable` auf Skill-Katalog und Matching-Queries, `@CacheEvict` auf Mutations.
- **Redis bewusst später**: erst bei mehreren Instanzen (Cache-Kohärenz), verteilten Rate-Limit-Buckets oder Sessions. Spring-Cache-Abstraktion macht Wechsel zum Config-Tausch.

## 5. Observability-Basics

- Strukturierte JSON-Logs + Correlation-ID pro Request.
- Error-Tracking: Sentry (Backend + Frontend).
- Health-Checks (Actuator `/health`).
- Später: Micrometer + Prometheus + Grafana für Latenz/Fehlerrate/DB-Pool.

## 6. Token-Härtung (größerer Umbau, eigene Session)

- **JWT aus localStorage in httpOnly-Cookie** + CSRF-Token. Größte echte XSS-Verbesserung, kostet Frontend-Umbau.
- **Refresh-Token-Rotation** mit Reuse-Detection: Token-Familie invalidieren bei Wiederverwendung = Diebstahl-Erkennung.

## 7. Weitere Security

- **Audit-Log**: Admin-Aktionen, Logins, Application-Decisions in Tabelle.
- **Passwort-Policy**: HaveIBeenPwned k-Anonymity-Check bei Registrierung/Change (externer API-Call → danach Circuit Breaker relevant).
- Actuator-Endpoints absichern.
- Später: 2FA (TOTP), Session-Management-UI.

## 8. Tests

- E2E mit Playwright: Kernflows (Login → Bewerbung → Annahme → Mitglied).
- Testcontainers für Integrationstests gegen echte Postgres/RabbitMQ.
- Component-Tests für Matching-Logik im Frontend.

## 9. Datenbank / Suche

- Index-Review: Explain-Plans für Matching-Queries.
- Volltextsuche: Postgres `tsvector` statt `LIKE '%...%'` für User-/Skill-Suche.

## 10. Features

- In-App-Notifications (Glocke im UI) — guter zweiter RabbitMQ-Konsument.
- Datei-Upload: Avatar, evtl. CV.
- DSGVO: Account-Löschung, Datenexport — Pflicht bei echten Usern.
- i18n falls Mehrsprachigkeit geplant.

## 11. Doku

- README mit Setup-Anleitung (Backend + Frontend + Secrets).
- ARCHITECTURE.md: Technologie-Begründungen.

## Bewusst verschoben ("add when", nicht "add now")

| Thema | Wann |
|---|---|
| Redis | Mehrere Backend-Instanzen, verteilte Rate-Limits/Sessions |
| Circuit Breaker (Resilience4j) | Externe HTTP-Calls (z.B. HaveIBeenPwned, KI-Matching) |
| 2FA | Nach Token-Härtung |
| Kubernetes | Wenn Compose an Grenzen stößt |
