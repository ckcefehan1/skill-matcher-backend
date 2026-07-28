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

## 5. Observability ✅ umgesetzt (Branch `feature/observability`)

**Gemacht:**

- Strukturierte Logs: `logback-spring.xml` — Profil `prod` = JSON via Logstash-Encoder, lokal lesbares Console-Pattern.
- Correlation-ID pro Request: `CorrelationIdFilter` schreibt MDC `correlationId`, echoed `X-Correlation-ID`-Header, akzeptiert eingehende IDs (max. 100 Zeichen).
- Health-Checks: Actuator `/health` inkl. K8s-Probes (`/health/liveness`, `/health/readiness`).
- Metriken: Micrometer + Prometheus-Registry, Endpoint `/actuator/prometheus`, Metrik-Tag `application=skill-matcher-backend`.
- Compose: Prometheus (9090) scrapt `host.docker.internal:8080/actuator/prometheus`, Grafana (3000, admin/admin) mit provisionierter Prometheus-Datasource und JVM-Micrometer-Dashboard (ID 4701).
- Tests: `CorrelationIdFilterTest`, `ObservabilityIT` (health/prometheus ohne Auth, Correlation-Echo).

**Nicht gemacht (offen):**

- Error-Tracking (Sentry o.ä.): bewusst rausgelassen — ohne echten DSN nur tote Dependency. Einrichten wenn Account existiert; Achtung: `sentry-spring-boot-4-starter` nötig, Jakarta-Variante inkompatibel mit Spring Boot 4.
- Auth auf `/actuator/prometheus`: aktuell unauthenticated (ponytail-Kommentar in `SecurityConfig`) — gehört zu Punkt 7.
- Alerting (Alertmanager, Regeln): nicht angefasst.

## 6. Token-Härtung ✅ umgesetzt

**Gemacht:**

- **httpOnly-Cookies**: Access-Token (`access_token`, path=/, 15min) und Refresh-Token (`refresh_token`, path=/api/auth, 7d) als httpOnly + SameSite=Strict Cookies statt localStorage. `AuthCookieService`, `CookieProperties` (`cookie.secure`, dev false). `AuthResponse` enthält keine Tokens mehr.
- **CSRF**: Security-7-Bordmittel `csrf.spa()` (= `CookieCsrfTokenRepository.withHttpOnlyFalse()` + SPA-Request-Handler, akzeptiert rohen Cookie-Wert im `X-XSRF-TOKEN`-Header, emittiert Cookie auch auf GETs). `GET /api/auth/csrf` als Bootstrap. `/ws/**` exempt (STOMP kann keinen Header). `CsrfAuthenticationStrategy` deaktiviert — wiped sonst das XSRF-Cookie bei jeder Filter-Auth (Session-Fixation-Schutz, bei STATELESS sinnlos). CORS `allowCredentials=true`.
- **Refresh-Rotation bei jedem Gebrauch** mit Reuse-Detection: `family_id` auf `refresh_tokens` (v0.23), wiederverwendeter rotierter Token → gesamte Family invalidiert (REQUIRES_NEW-TX, sonst rollbackt die 401 die Revocation). Strikte Variante ohne Grace-Window (Best Practice nach OAuth 2.0 BCP; ponytail-Kommentar im Code). `findByTokenHash` hält pessimistisches Write-Lock — parallele Refreshes desselben Tokens serialisieren, der Verlierer läuft in die Reuse-Detection.
- **SameSite=Strict + CSRF-Token bewusst doppelt**: Strict allein würde CSRF schon verhindern, das Token bleibt als Defense-in-Depth (und schützt, sobald ein Browser SameSite-Bugs hat oder der Cookie-Scope mal gelockert wird).
- **Frontend**: auth-store ohne Tokens (nur `user`, persist version bump), axios `withCredentials` + XSRF-Header aus Cookie, 401→Refresh (leerer Body)→Retry, Guards prüfen `user`. Orval-Client regeneriert. `bootstrapCsrf()` beim App-Start.
- `JwtAuthenticationFilter`: Cookie zuerst, Bearer-Header als Fallback (Swagger/Tests/STOMP).

**Nicht gemacht (offen):**

- WebSocket-Auth per Cookie/Ticket: Frontend nutzt noch kein STOMP. Bei Adoption prüfen — STOMP-CONNECT kann keine Browser-Cookies setzen, aktuell läuft es über Bearer im Header (Frontend hat keinen Zugriff mehr auf Token → Ticket-Endpoint nötig).
- ~~Bekannter Tradeoff: zwei Tabs, die exakt gleichzeitig refreshen, loggen sich gegenseitig aus~~ → gelöst via Web Locks (`navigator.locks.request('auth-refresh')` im axios-Refresh-Pfad): Cross-Tab-Single-Flight, zweiter Tab sendet frisches Cookie statt stalem Token.

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
| WebSocket-Auth per Ticket-Endpoint | Sobald Frontend STOMP nutzt (JS sieht httpOnly-Cookie nicht) |
