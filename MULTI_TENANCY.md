# Multi-Tenancy Konzept

Status: Backend umgesetzt auf `feat/multi-tenancy` (Migrationen v0.26–v0.28, Superadmin-API, Self-Registrierung, Standalone-Bootstrap). Frontend steht noch aus — siehe [Frontend-Impact](#frontend-impact). Der 2026-08-01 verworfene erste Versuch ist nicht Grundlage dieses Dokuments.

## Ziel

Matchpoint wird in zwei Betriebsformen ausgeliefert:

- **SaaS** — eine Instanz, viele Companies (Tenants), strikte Datenisolation.
- **Self-Deploy / On-Prem** — ein Unternehmen betreibt die gleiche Instanz intern, mit genau einer Company.

Eine Codebase, ein Docker-Image für beides. On-Prem ist kein Sonderfall im Code, sondern nur eine SaaS-Instanz mit einer einzigen Company-Zeile.

## Grundmodell: Shared Schema + Discriminator

Alle Tenants teilen dieselbe Datenbank und dasselbe Schema. Jede tenant-eigene Tabelle bekommt eine `company_id`-Spalte (FK auf `companies`), die jede Zeile einem Tenant zuordnet.

Alternativen und warum nicht:

| Modell | Warum nicht |
|---|---|
| DB pro Tenant | N Instanzen/Migrationen für SaaS-Betrieb, Provisioning-Aufwand pro Kunde, kein shared Skill-Katalog |
| Schema pro Tenant | Migration-Laufzeit skaliert mit Tenant-Anzahl, gleiche Nachteile in kleiner |
| Discriminator-Spalte | Eine Migration für alle, eine Codebase, On-Prem = Trivialfall |

## Entscheidungen (abgestimmt 2026-08-01)

1. **Skill-Katalog ist global** — `skills` und `skill_relations` bekommen **kein** `company_id`. Alle Companies nutzen denselben Katalog. Kein Duplikat-Problem, `SKILL_CATALOG`-Cache bleibt tenant-neutral.
2. **Kein Postgres Row Level Security** — Enforcement passiert ausschließlich im ORM über Hibernates `@TenantId`. RLS ist später als reine Migration nachrüstbar, falls SaaS-Kunden DB-seitige Isolation nachweisen müssen. Kein Code-Eingriff nötig dafür.
3. **Email bleibt global unique** — kein Tenant-Picker beim Login, Login-Flow unverändert. Dieselbe Email kann nicht in zwei Companies existieren.
4. **Neue Rolle `SUPERADMIN`** — Plattform-Betreiber, keiner Company zugeordnet, sieht alles (root-Kontext). `ADMIN` bleibt Company-intern.
5. **Companies registrieren sich selbst** — öffentlicher Registrierungs-Endpunkt im SaaS-Modus. User-Self-Signup gibt es weiterhin nicht: Mitarbeiter werden pro Company per Invitation hinzugefügt. Nur die *Company* (mit ihrem ersten ADMIN) meldet sich selbst an. Im On-Prem-Modus ist Registrierung deaktiviert.

## Datenmodell

### Neue Tabelle `companies`

| Spalte | Typ | Bemerkung |
|---|---|---|
| id | uuid/string PK | wie üblich via `AuditingBaseEntity` |
| name | varchar, unique | Anzeigename |
| street | varchar, NOT NULL | Pflicht bei Registrierung |
| zip | varchar, NOT NULL | |
| city | varchar, NOT NULL | |
| country | varchar(2), NOT NULL | ISO-3166 Alpha-2 (`DE`, `AT`, …) |
| industry | varchar, nullable | Branche, optional |
| company_size | varchar, nullable | Größenklasse als Enum-String (`SIZE_1_10`, `SIZE_11_50`, `SIZE_51_200`, `SIZE_201_1000`, `SIZE_1000_PLUS`), optional |
| website | varchar, nullable | optional |
| is_enabled | boolean | Deaktivierung sperrt Tenant komplett (SaaS-Kündigung) |

Adresse ist Pflicht (B2B-Rechnungs-/Vertragsdaten, auch für On-Prem-Kunden sinnvoll). `industry`, `company_size`, `website` optional — bewusst kleiner Satz, Erweiterung später über nullable Spalten ohne Migrationsschmerz.

### Tenant-scoped Tabellen (bekommen `company_id`, NOT NULL, FK, Index)

- `users`
- `projects`
- `project_members`
- `project_skills`
- `project_applications`
- `user_skills`
- `user_availability`
- `conversations`
- `chat_messages`
- `notifications`
- `invitation_tokens`

### Nullable `company_id` (root-Kontexte)

- `audit_logs` — SUPERADMIN-Aktionen haben keinen Tenant; nullable, kein Tenant-Filter auf der Entity.
- `refresh_tokens`, `password_reset_tokens` — Token-Flows laufen ohne Tenant-Kontext (Login/Reset vor Authentifizierung). Lookup erfolgt über Token-Hash bzw. global unique Email, daher kein Scoping nötig.

### Global (unverändert)

- `roles`, `skills`, `skill_relations`

## Tenant-Enforcement

### `@TenantId` (Hibernate 6+, Jakarta-Standard)

Neue `@MappedSuperclass TenantAwareEntity extends AuditingBaseEntity`:

```kotlin
@MappedSuperclass
abstract class TenantAwareEntity(
    @TenantId
    @Column(name = "company_id", nullable = false, updatable = false)
    var companyId: String? = null,
) : AuditingBaseEntity()
```

Alle tenant-scoped Entities erben davon. Hibernate hängt bei gesetztem Tenant automatisch `WHERE company_id = ?` an jede Query, jeden Join-Load und jeden Insert — kein Repository-Umbau.

### `TenantContext` + Resolver

- `TenantContext` — ThreadLocal<String>, Methoden `set/get/clear`.
- `CurrentTenantIdentifierResolver`-Implementierung liest `TenantContext`. Liefert `null` → **kein Filter** (root-Kontext). Das ist gewollt für Login, Token-Flows, SUPERADMIN, Hintergrund-Threads.
- Registrierung via `HibernatePropertiesCustomizer` (`hibernate.tenant_identifier_resolver`).

### Wer setzt den Tenant

| Kontext | Tenant gesetzt? | Quelle |
|---|---|---|
| `JwtAuthenticationFilter` | ja, vor erstem DB-Zugriff | `companyId`-Claim aus JWT, `finally { clear() }` |
| `WebSocketAuthInterceptor` | ja, pro STOMP-Message | Claim aus WS-Ticket/JWT |
| RabbitMQ-Consumer (Mail, Notifications) | ja, manuell im Listener | `companyId`-Feld in der Message |
| Login, Refresh, Password-Reset | nein (root) | — |
| SUPERADMIN-Requests | nein (root) | Claim fehlt absichtlich |
| Tests | explizit im Setup | `TenantContext.set(...)` |

Root = ungefiltert ist die kritische Stelle des Designs: jeder neue Codepfad ohne gesetzten Tenant sieht **alle** Daten. Deshalb: Tenant-Pflicht-Regel im Review (jeder neue `@Service`-Pfad entweder im JWT-Filter-Kontext oder explizit `TenantContext.set(...)`), plus Cross-Tenant-Integrationstests als Sicherheitsnetz.

### Fallstricke von Hibernates `@TenantId`

Drei Eigenheiten, die beim Umsetzen Zeit gekostet haben:

1. **Der Tenant wird beim Öffnen der Session aufgelöst, nicht pro Statement.** `TenantContext` innerhalb einer laufenden `@Transactional`-Methode zu ändern hat keinerlei Wirkung auf die Session — der Wert muss stehen, *bevor* die Transaktion beginnt. Ein `withTenant { … }`-Helper mitten im Service ist deshalb wirkungslos.
2. **Root braucht ein Sentinel.** `resolveCurrentTenantIdentifier()` darf nicht `null` liefern, sonst schreibt Hibernate `NULL` in die NOT-NULL-Spalte. `TenantIdentifierResolver` liefert stattdessen `__root__` und meldet es über `isRoot()` — nur dann erlaubt `TenantIdGeneration` einer Zeile, ihre `companyId` selbst zu setzen. Kehrseite: wer im Root-Kontext eine tenant-eigene Zeile *ohne* explizite `companyId` speichert, schreibt wörtlich `__root__` in die Spalte. Der FK auf `companies` fängt das ab — laut, aber erst zur Laufzeit.
3. **Hibernate schreibt den generierten Wert nicht auf das Objekt zurück.** Er entsteht erst beim Flush. Wer `companyId` vorher liest — etwa um ein JWT zu bauen — sähe `null`. `TenantAwareEntity` initialisiert das Feld deshalb schon im Konstruktor aus dem `TenantContext`.

## Auth & Rollen

- **JWT** bekommt zusätzlichen Claim `companyId` (String). SUPERADMIN-Tokens haben den Claim nicht.
- `SecurityUser` trägt `companyId`, Controller können es bei Bedarf direkt nutzen (z.B. explizite Company-Zuweisung beim Anlegen, weil root-Kontexte keinen Session-Tenant erben).
- `RoleName` um `SUPERADMIN` erweitern, Migration legt Rollen-Zeile an.
- Login: `findByEmail` im root-Kontext (Email global unique) → User → Token mit dessen `companyId`.
- `SecurityConfig`: `/api/superadmin/**` → `hasRole('SUPERADMIN')`. Bestehende `/api/admin/**` bleibt `ADMIN` (Company-intern durch Tenant-Filter).
- Deaktivierte Company (`is_enabled = false`): `JwtAuthenticationFilter` lehnt Requests mit `403` ab (Check gecacht oder claim-seitig, nicht pro Request in DB).

## On-Prem / Self-Deploy

Gleiche Binary. Beim Start prüft ein `StandaloneDataInitializer` (ApplicationRunner):

- Existiert keine Company → Default-Company anlegen, Name aus Property.
- Existiert kein Admin → initialen ADMIN-Account gemäß Properties anlegen (Passwort-Wechsel beim ersten Login via bestehendem Invitation-Flow).

Properties nach Projekt-Konvention als typed `@ConfigurationProperties` in `config/properties/`:

```properties
app.standalone.enabled=true
app.standalone.company-name=Muster GmbH
app.standalone.company-street=Musterstraße 1
app.standalone.company-zip=12345
app.standalone.company-city=Musterstadt
app.standalone.company-country=DE
app.standalone.admin-email=admin@muster.local
```

Adress-Properties pflicht bei `enabled=true` (Schema verlangt NOT NULL); optionale Felder (`industry`, `company-size`, `website`) analog setzbar.

SaaS-Betrieb: `app.standalone.enabled=false`, Companies werden über die Superadmin-API angelegt.

## Superadmin-API

`/api/superadmin/companies` — CRUD für Companies: anlegen (inkl. erstem ADMIN-Invite über bestehenden Invitation-Service), auflisten, aktivieren/deaktivieren. Kein Cross-Tenant-User-Zugriff in Iteration 1 — Company-Interna verwaltet der jeweilige ADMIN.

## Self-Registrierung (nur SaaS)

Öffentlicher Flow, mit dem sich ein Unternehmen eigenständig anmeldet — ohne SUPERADMIN-Zutat.

### Flow

1. `POST /api/public/companies/register` (unauthenticated) mit Pflichtfeldern `{ companyName, street, zip, city, country, adminEmail }` und optional `{ industry, companySize, website }`. Jakarta-Validation im Request-DTO (`@NotBlank` auf Adresse, `@Size(max=2)` auf `country`).
2. Backend legt Company an (`is_enabled = false`) + ADMIN-User (deaktiviert) und stößt den **bestehenden Invitation-Flow** an: Mail mit Accept-Link an `adminEmail`. Kein neuer Token-Typ, keine neue Mail-Logik — `InvitationTokenModel` und Templates wiederverwenden.
3. Admin klickt Link, setzt Passwort über bestehende Invite-Acceptance → User aktiviert, Company auf `is_enabled = true` gesetzt. Invite-Acceptance ist gleichzeitig der Email-Besitz-Nachweis.
4. Danach normaler Login; der ADMIN lädt seine Mitarbeiter wie bisher per Invitation ein.

### Leitplanken

- **Nur SaaS:** Endpoint liefert `404`, wenn `app.standalone.enabled=true` (On-Prem = genau eine Company, Selbstregistrierung sinnlos und Angriffsfläche).
- **SecurityConfig:** `/api/public/**` ohne Auth freigeben — bewusst klein halten, nur dieser eine Endpoint.
- **Missbrauch:** Rate-Limit auf IP + Email, Company-Name unique, keine Fehlermeldung die verrät ob eine Email bereits existiert (gleiche Antwort wie Erfolg).
- **Tenant-Kontext:** Registrierung läuft im root-Kontext; `companyId` wird explizit am Entity gesetzt, weil kein Session-Tenant existiert.
- **Audit-Log:** Eintrag `COMPANY_REGISTERED` mit der neuen `company_id`.
- **Zombie-Companies:** Registrierungen, deren Invite nie akzeptiert wird, räumt ein Scheduled-Job nach Ablauf der Invitation-Gültigkeit weg (Company + User + Token löschen).

## Querschnitt: was mitdenken

- **Caffeine-Caches** — `MATCHING_CANDIDATES`, `MATCHING_PROJECTS_FOR_USER` Keys müssen `companyId` enthalten (Key-Expression um `TenantContext.get()` erweitern). `SKILL_CATALOG` bleibt global, unverändert.
- **RabbitMQ-Messages** (Mail, Notification) — Payload bekommt `companyId`; Listener setzt `TenantContext` vor Verarbeitung, `clear()` im finally.
- **Audit-Log** — `audit_logs.company_id` nullable; Einträge im Tenant-Kontext erben den Tenant, SUPERADMIN-Einträge bleiben null.
- **OpenAPI/orval** — neue Superadmin-Endpunkte landen im Spec, Frontend-Client neu generieren.

## Migrationen (Reihenfolge)

1. **v0.26** — Tabelle `companies` anlegen; Rolle `SUPERADMIN` einfügen.
2. **v0.27** — `company_id`-Spalten (nullable) auf alle tenant-scoped Tabellen + Default-Company einfügen + Backfill aller Bestandsdaten auf Default-Company.
3. **v0.28** — `company_id` NOT NULL setzen, FK-Constraints, Indizes (`company_id` als führende Spalte auf allen gefilterten Tabellen; bestehende Unique-Constraints wie `users.email` bleiben global).

Bestehende On-Prem-Bestandskunden landen durch den Backfill automatisch in der Default-Company — für sie ändert sich nichts.

## Frontend-Impact

- Login, Cookies, CSRF: unverändert.
- Neue öffentliche Seite „Unternehmen registrieren" — Formular: Firmenname, Adresse (Straße, PLZ, Stadt, Land als Select), adminEmail, optional Branche/Größe/Website; danach „Mail verschickt"-Hinweis — keine Info ob Email existiert. Nur im SaaS-Modus verlinkt; Invite-Acceptance-Seite existiert bereits und wird wiederverwendet.
- Neuer Bereich „Company-Verwaltung" nur für `SUPERADMIN` (Rollen-Gate im Router).
- Alle bestehenden Features laufen unverändert im Tenant des eingeloggten Users — kein UI-Tenant-Switcher (außer ggf. später für SUPERADMIN-Impersonation, nicht Iteration 1).
- **Merge-Kopplung:** Frontend und Backend gehen gemeinsam live — Login stellt sonst keinen `companyId`-Kontext her.

## Testplan

- `AbstractIntegrationTest` setzt `TenantContext` im Setup (zwei Fixture-Companies A/B).
- Kern-Test: Tenant A sieht Users/Projekte/Skills-Zuordnungen von Tenant B nicht — über Service-Layer, nicht nur Repositories (inkl. `searchChatPartners`, Matching, `searchByRole`).
- Root-Kontext-Test: Login-Flow und SUPERADMIN-Endpunkte funktionieren ohne gesetzten Tenant.
- Cache-Test: gleiche Projekt-Ids in zwei Tenants liefern keine Cache-Treffer übergreifend.
- Migration-Test: Backfill auf bestehender Datenbasis (Testcontainers).
- Registrierungs-Test: `POST /api/public/companies/register` erzeugt deaktivierte Company + verschickt Invite; Invite-Acceptance aktiviert beides; Endpoint blockt im Standalone-Modus; keine Email-Existenz-Leaks in Responses.

## Explizit nicht drin

- Postgres RLS (siehe Entscheidung 2).
- Schema/DB-per-Tenant.
- Billing, Quotas, Tenant-spezifisches Branding.
- SUPERADMIN-Impersonation / Cross-Tenant-User-Verwaltung.
- Tenant-Auswahl beim Login (durch global unique Emails unnötig).
