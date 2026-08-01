# Multi-Tenancy Konzept

Status: Backend umgesetzt auf `feat/multi-tenancy` (Migrationen v0.26–v0.28, Superadmin-API, Self-Registrierung, Standalone-Bootstrap, Superadmin-Bootstrap). Frontend steht noch aus — siehe [Frontend-Impact](#frontend-impact). Der 2026-08-01 verworfene erste Versuch ist nicht Grundlage dieses Dokuments.

Die Mängelliste aus dem Code-Review (2026-08-01) ist vollständig abgearbeitet: Fail-closed-Tenant-Default mit explizitem `runAsRoot`, Rollen-Allowlist im Admin-Pfad, Rollen-Seeding per Migration, Superadmin-Bootstrap per `app.superadmin.email`, Session-/Token-Revoke beim Company-Deaktivieren, Deduplikation Company-Provisioning, DTO-Trennung public/superadmin, Zombie-Job mit Advisory-Lock, Service-Layer- und Cross-Tenant-Tests mit echten JWTs.

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
| self_registered | boolean | Selbstregistriert statt per Superadmin angelegt; steuert die Aktivierung per Invite-Acceptance und den Zombie-Cleanup |

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

### Ohne `company_id`

- `roles`, `skills`, `skill_relations` — global, unverändert.
- `refresh_tokens`, `password_reset_tokens` — Token-Flows laufen ohne Tenant-Kontext (Login/Reset vor Authentifizierung), Lookup über Token-Hash bzw. global unique Email. Bewusst gar keine Spalte statt einer nullable: eine Spalte, die keine Entity mappt, bliebe dauerhaft NULL ohne FK und Index.

## Tenant-Enforcement

### `@TenantId` (Hibernate 6+, Jakarta-Standard)

Neue `@MappedSuperclass TenantAwareEntity extends AuditingBaseEntity`:

```kotlin
@MappedSuperclass
abstract class TenantAwareEntity(
    @TenantId
    @Column(name = "company_id", nullable = false, updatable = false)
    var companyId: String = TenantContext.get() ?: "",
) : AuditingBaseEntity()
```

Nicht-nullable wie die Spalte: alles danach müsste sonst ein Feld null-behandeln, das nicht null sein kann. Der `""`-Default überlebt keinen Flush, weil der FK auf `companies` ihn ablehnt.

Alle tenant-scoped Entities erben davon. Hibernate hängt bei gesetztem Tenant automatisch `WHERE company_id = ?` an jede Query, jeden Join-Load und jeden Insert — kein Repository-Umbau.

### `TenantContext` + Resolver

- `TenantContext` — ThreadLocal<String>, Methoden `set/get/clear`, dazu `runAsRoot {}` und `withTenant(id) {}`.
- **Fail-closed:** `CurrentTenantIdentifierResolver` liefert bei leerem Kontext nur dann das Root-Sentinel, wenn der Pfad sich explizit als Root deklariert hat (`runAsRoot`/`allowRoot`). Sonst wirft er — ein vergessener Tenant ist ein lauter Fehler, kein stiller Vollzugriff.
- Registrierung via `HibernatePropertiesCustomizer` (`hibernate.tenant_identifier_resolver`).

### Wer setzt den Tenant

| Kontext | Tenant gesetzt? | Quelle |
|---|---|---|
| `JwtAuthenticationFilter` | ja, vor erstem DB-Zugriff | `companyId`-Claim aus JWT, `finally { clear() }` |
| Requests ohne Claim (öffentlich, SUPERADMIN) | nein | Filter deklariert `runAsRoot` um die Filterkette |
| `WebSocketAuthInterceptor` | ja, pro STOMP-Message | Claim aus WS-Ticket/JWT, sonst explizites Root |
| `PresenceService` (STOMP-Event-Threads) | ja, manuell | `withTenant(user.companyId)` um die Partner-Suche |
| RabbitMQ-Consumer (Mail, Notifications) | ja, manuell im Listener | `companyId`-Feld in der Message, sonst explizites Root |
| Scheduled Jobs (Zombie-Cleanup, Skill-Graph, WS-Revalidator) | nein | `runAsRoot` — sie arbeiten bewusst tenant-übergreifend |
| `StandaloneDataInitializer`, `SuperadminBootstrapInitializer` | nein | `runAsRoot` |
| Tests | explizit im Setup | `TenantContext.set(...)`, Cleanup in `runAsRoot` |

Root ist damit eine bewusste Entscheidung pro Pfad, nicht der Default. Neue `@Service`-Pfade müssen entweder im JWT-Filter-Kontext laufen oder `TenantContext.set(...)`/`runAsRoot` aufrufen — der Resolver macht jeden vergessenen Pfad sofort sichtbar.

### Fallstricke von Hibernates `@TenantId`

Drei Eigenheiten, die beim Umsetzen Zeit gekostet haben:

1. **Der Tenant wird beim Öffnen der Session aufgelöst, nicht pro Statement.** `TenantContext` innerhalb einer laufenden `@Transactional`-Methode zu ändern hat keinerlei Wirkung auf die Session — der Wert muss stehen, *bevor* die Transaktion beginnt. Ein `withTenant { … }`-Helper mitten im Service ist deshalb wirkungslos.
2. **Root braucht ein Sentinel.** `resolveCurrentTenantIdentifier()` darf nicht `null` liefern, sonst schreibt Hibernate `NULL` in die NOT-NULL-Spalte. `TenantIdentifierResolver` liefert stattdessen `__root__` und meldet es über `isRoot()` — nur dann erlaubt `TenantIdGeneration` einer Zeile, ihre `companyId` selbst zu setzen. Kehrseite: wer im Root-Kontext eine tenant-eigene Zeile *ohne* explizite `companyId` speichert, schreibt den leeren Konstruktor-Default in die Spalte. Der FK auf `companies` fängt das ab — laut, aber erst zur Laufzeit.
3. **Hibernate schreibt den generierten Wert nicht auf das Objekt zurück.** Er entsteht erst beim Flush. Wer `companyId` vorher liest — etwa um ein JWT zu bauen — sähe nur den Default. `TenantAwareEntity` initialisiert das Feld deshalb schon im Konstruktor aus dem `TenantContext`.

## Auth & Rollen

- **JWT** bekommt zusätzlichen Claim `companyId` (String). SUPERADMIN-Tokens haben den Claim nicht.
- `SecurityUser` trägt `companyId`, Controller können es bei Bedarf direkt nutzen (z.B. explizite Company-Zuweisung beim Anlegen, weil root-Kontexte keinen Session-Tenant erben).
- `RoleName` um `SUPERADMIN` erweitern, Migration legt Rollen-Zeile an.
- Login: `findByEmail` im root-Kontext (Email global unique) → User → Token mit dessen `companyId`.
- `SecurityConfig`: `/api/superadmin/**` → `hasRole('SUPERADMIN')`, zusätzlich `@PreAuthorize` am Controller wie überall sonst im Repo. Bestehende `/api/admin/**` bleibt `ADMIN` (Company-intern durch Tenant-Filter).
- **Rollen-Allowlist im Admin-Pfad:** `AdminUserService` vergibt nur `ADMIN`, `PROJECTMANAGER`, `EMPLOYER`; alles andere ist `403`. Ohne die Schranke könnte ein Company-ADMIN sich selbst `SUPERADMIN` zuweisen — der nächste Login käme ohne `companyId`-Claim, und der Tenant-Filter fiele für ihn komplett weg. Die neue Rolle hat damit eine bestehende, lose typisierte API scharf gemacht.
- Deaktivierte Company (`is_enabled = false`): `JwtAuthenticationFilter` lehnt HTTP-Requests mit `403` ab, `WebSocketAuthInterceptor` lehnt STOMP-CONNECT ab (sonst behielte der gesperrte Tenant Chat, Presence und Notifications), und `CompanyService.setEnabled(false)` widerruft Refresh-Tokens und trennt offene Sessions — gleiche Semantik wie das Deaktivieren eines einzelnen Users. Unbekannte `companyId` gilt als deaktiviert: der Zombie-Job kann die Zeile gelöscht haben, während Tokens sie noch referenzieren.

## On-Prem / Self-Deploy

Gleiche Binary. Beim Start prüft ein `StandaloneDataInitializer` (ApplicationRunner):

- Existiert keine Company → Default-Company anlegen, Name aus Property. Die Platform-Company aus v0.26 zählt dabei nicht mit: sie steht in jeder Installation und ist nur der Anker für SUPERADMIN-Accounts, nicht der Kunden-Tenant.
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

Damit die Properties überhaupt greifen, entsteht die Default-Company aus v0.27 nur auf Bestandsinstallationen (preCondition auf vorhandene `users`-Zeilen). Auf frischer DB gäbe es nichts zu backfillen, und `findAll().firstOrNull()` im Initializer würde sonst immer diese Zeile abgreifen — der Kunde bekäme einen Tenant „Default Company" mit Adresse „Unknown".

SaaS-Betrieb: `app.standalone.enabled=false`, Companies werden über die Superadmin-API angelegt.

## Superadmin-API

`/api/superadmin/companies` — CRUD für Companies: anlegen (inkl. erstem ADMIN-Invite über bestehenden Invitation-Service), auflisten, aktivieren/deaktivieren. Kein Cross-Tenant-User-Zugriff in Iteration 1 — Company-Interna verwaltet der jeweilige ADMIN.

Superadmin- und Public-Endpunkt teilen sich einen Provisioning-Pfad (`CompanyService.provision`), aber nicht das Request-DTO: `CreateCompanyRequest` und `RegisterCompanyRequest` sind getrennt, damit ein später ergänztes Superadmin-Feld nicht versehentlich öffentlich setzbar wird.

### Erster SUPERADMIN

`app.superadmin.email` setzen (beide Betriebsformen) — `SuperadminBootstrapInitializer` legt den Account einmalig an und lädt ihn über den normalen Invitation-Flow ein. Leere Property = kein Bootstrap. Ohne diesen Pfad wäre `/api/superadmin/**` in einer echten Umgebung unerreichbar.

Der Account hängt an der „Platform"-Company aus v0.26: `users.company_id` ist NOT NULL, ein SUPERADMIN gehört aber zu keinem Kunden. Aus dem JWT bleibt die Company draußen, weil `generateAccessToken` den Claim für diese Rolle weglässt.

## Self-Registrierung (nur SaaS)

Öffentlicher Flow, mit dem sich ein Unternehmen eigenständig anmeldet — ohne SUPERADMIN-Zutat.

### Flow

1. `POST /api/public/companies/register` (unauthenticated) mit Pflichtfeldern `{ name, street, zip, city, country, adminEmail }` und optional `{ industry, companySize, website }`. Jakarta-Validation im Request-DTO (`@NotBlank` auf Adresse, `@Pattern("[A-Z]{2}")` auf `country`).
2. Backend legt Company an (`is_enabled = false`) + ADMIN-User (deaktiviert) und stößt den **bestehenden Invitation-Flow** an: Mail mit Accept-Link an `adminEmail`. Kein neuer Token-Typ, keine neue Mail-Logik — `InvitationTokenModel` und Templates wiederverwenden.
3. Admin klickt Link, setzt Passwort über bestehende Invite-Acceptance → User aktiviert, Company auf `is_enabled = true` gesetzt. Invite-Acceptance ist gleichzeitig der Email-Besitz-Nachweis.
4. Danach normaler Login; der ADMIN lädt seine Mitarbeiter wie bisher per Invitation ein.

### Leitplanken

- **Nur SaaS:** Endpoint liefert `404`, wenn `app.standalone.enabled=true` (On-Prem = genau eine Company, Selbstregistrierung sinnlos und Angriffsfläche).
- **SecurityConfig:** `/api/public/**` ohne Auth freigeben — bewusst klein halten, nur dieser eine Endpoint.
- **Missbrauch:** Rate-Limit pro IP auf `/api/public/companies/register`, Company-Name unique. Weder eine belegte Email noch ein belegter Firmenname erzeugen eine abweichende Antwort — sonst wäre die Kundenliste über den Namen enumerierbar. Der Superadmin-Pfad antwortet in beiden Fällen weiterhin mit `409`.
- **Tenant-Kontext:** Registrierung läuft im root-Kontext; `companyId` wird explizit am Entity gesetzt, weil kein Session-Tenant existiert.
- **Audit-Log:** Eintrag `COMPANY_REGISTERED` mit der neuen `company_id`.
- **Aktivierung:** liegt in `CompanyService`, nicht im Invitation-Service — dort gibt es die Cache-Eviction schon deklarativ. Die Invite-Acceptance meldet nur ein `InvitationAcceptedEvent`.
- **Zombie-Companies:** Registrierungen, deren Invite nie akzeptiert wird, räumt ein Scheduled-Job nach Ablauf der Invitation-Gültigkeit weg (Company + User + Token löschen). Er hält einen Postgres-Advisory-Lock, damit bei mehreren Instanzen nicht alle Knoten dieselben Zeilen löschen, und ist im Standalone-Modus aus — dort gibt es keine Selbstregistrierung.

## Querschnitt: was mitdenken

- **Caffeine-Caches** — `MATCHING_CANDIDATES`, `MATCHING_PROJECTS_FOR_USER` Keys müssen `companyId` enthalten (Key-Expression um `TenantContext.get()` erweitern). `SKILL_CATALOG` bleibt global, unverändert.
- **RabbitMQ-Messages** (Mail, Notification) — Payload bekommt `companyId`; Listener setzt `TenantContext` vor Verarbeitung, `clear()` im finally.
- **`COMPANY_ENABLED`-Cache ist prozesslokal** — Caffeine mit 60s TTL, `@CacheEvict` wirkt nur auf der eigenen Instanz. Bei mehr als einer Instanz arbeitet ein gesperrter Tenant auf den anderen Knoten bis zum TTL-Ablauf weiter. Akzeptiert, solange SaaS single-instance läuft; sonst geteilter Cache.
- **Audit-Log** — `audit_logs.company_id` nullable; Einträge im Tenant-Kontext erben den Tenant, SUPERADMIN-Einträge bleiben null.
- **OpenAPI/orval** — neue Superadmin-Endpunkte landen im Spec, Frontend-Client neu generieren.

## Migrationen (Reihenfolge)

1. **v0.26** — Tabelle `companies` anlegen; Rollen `SUPERADMIN`, `ADMIN`, `PROJECTMANAGER`, `EMPLOYER` seeden (die drei letzten idempotent, Bestandsinstallationen haben sie schon); Platform-Company als Anker für SUPERADMIN-Accounts.
2. **v0.27** — `company_id`-Spalten (nullable) auf alle tenant-scoped Tabellen + Backfill aller Bestandsdaten auf eine Default-Company. Deren Insert steht unter einer preCondition auf vorhandene `users` — frische Installationen bekommen sie nicht.
3. **v0.28** — `company_id` NOT NULL setzen, FK-Constraints, Indizes (`company_id` als führende Spalte auf allen gefilterten Tabellen; bestehende Unique-Constraints wie `users.email` bleiben global).

Rollen-Seeding gehört in die Migration, nicht in den `StandaloneDataInitializer`: der hängt an `app.standalone.enabled=true`, und auf einer frischen SaaS-DB fehlte damit die `ADMIN`-Zeile, die `POST /api/public/companies/register` braucht.

Bestehende On-Prem-Bestandskunden landen durch den Backfill automatisch in der Default-Company — für sie ändert sich nichts.

## Frontend-Impact

- Login, Cookies, CSRF: unverändert.
- Neue öffentliche Seite „Unternehmen registrieren" — Formular: Firmenname, Adresse (Straße, PLZ, Stadt, Land als Select), adminEmail, optional Branche/Größe/Website; danach „Mail verschickt"-Hinweis — keine Info, ob Email oder Firmenname schon existiert. Nur im SaaS-Modus verlinkt; Invite-Acceptance-Seite existiert bereits und wird wiederverwendet.
- **Modus-Erkennung:** `GET /api/public/config` liefert `registrationEnabled`. Zur Buildzeit geht das nicht — ein Vite-Flag landet fest im Bundle und würde zwei Images erzwingen. `app.standalone.enabled` bleibt die einzige Quelle, das Frontend liest sie zur Laufzeit.
- Neuer Bereich „Company-Verwaltung" nur für `SUPERADMIN` (Rollen-Gate im Router).
- Alle bestehenden Features laufen unverändert im Tenant des eingeloggten Users — kein UI-Tenant-Switcher (außer ggf. später für SUPERADMIN-Impersonation, nicht Iteration 1).
- **Merge-Kopplung:** Frontend und Backend gehen gemeinsam live — Login stellt sonst keinen `companyId`-Kontext her.

## Testplan

- `AbstractIntegrationTest` setzt `TenantContext` im Setup (zwei Fixture-Companies A/B), Cleanup in `runAsRoot`.
- `TenantIsolationIT` — Tenant A sieht Users/Projekte/Chat-Partner von Tenant B nicht, geprüft über den **Service-Layer**. Repositories allein würden einen Codepfad übersehen, der den Tenant unterwegs verliert.
- `CrossTenantSecurityIT` — dasselbe über Controller mit echten JWTs. Genau dieser Test fängt ein fehlendes `beforeHandle` oder einen Controller, der am Tenant vorbei lädt.
- Root-Kontext-Test: nur explizit deklarierter Root ist ungefiltert; Login-Flow und SUPERADMIN-Endpunkte funktionieren ohne gesetzten Tenant. `PresenceServiceTest` deckt die STOMP-Event-Threads ab.
- Cache-Test: gleiche Projekt-Ids in zwei Tenants liefern keine Cache-Treffer übergreifend.
- Migration-Test: Backfill auf bestehender Datenbasis (Testcontainers).
- Registrierungs-Test: `POST /api/public/companies/register` erzeugt deaktivierte Company + verschickt Invite; Invite-Acceptance aktiviert beides; Endpoint blockt im Standalone-Modus; weder Email- noch Firmennamen-Kollision ist an der Response erkennbar.

## Explizit nicht drin

- Postgres RLS (siehe Entscheidung 2).
- Schema/DB-per-Tenant.
- Billing, Quotas, Tenant-spezifisches Branding.
- SUPERADMIN-Impersonation / Cross-Tenant-User-Verwaltung.
- Tenant-Auswahl beim Login (durch global unique Emails unnötig).

---
---

# ANHANG: Offene Punkte aus dem Code-Review

> Alles ab hier ist **nicht Teil des Konzepts**, sondern die Mängelliste aus dem Review von
> `feat/multi-tenancy` (2026-08-01). Beschreibt den Ist-Zustand des Branches, nicht das Soll-Design.
> Wenn ein Punkt erledigt ist: hier abhaken oder streichen. Bei leerer Liste kann der ganze Anhang weg.

## Blocker — kein Merge, solange offen

### B1. Privilege Escalation: Tenant-ADMIN kann sich zu SUPERADMIN machen

- [x] Zuweisbare Rollen im Admin-Pfad auf eine Allowlist begrenzen, `SUPERADMIN` mit 403 ablehnen.

`AdminUserService.updateUserRole` (`AdminUserService.kt:104`) und `createUser` (`:31`) nehmen einen
freien String aus dem Request und schlagen ihn in der globalen `roles`-Tabelle nach. v0.26 hat dort
`SUPERADMIN` eingefügt. `/api/admin/**` verlangt nur `hasRole('ADMIN')`.

```
PATCH /api/admin/users/{eigene-id}/role   {"role": "SUPERADMIN"}
```

Danach erzwingt `revokeAllUserTokens` einen Neu-Login, `JwtService.generateAccessToken:49` lässt den
`companyId`-Claim weg, `TenantIdentifierResolver:17` liefert `__root__`, Hibernate filtert nicht mehr.
Ergebnis: Lese- und Schreibzugriff auf alle Tenants plus komplettes `/api/superadmin/**`.

Ursache liegt nicht im neuen Code — die neue Rolle hat eine bestehende lose API scharf gemacht.
`RoleName`-Enum statt `String` im DTO wäre der saubere Zug.

### B2. Root = ungefiltert ist fail-open

- [x] Default umdrehen: kein Tenant gesetzt = Deny.
- [x] Explizites `runAsRoot { }` für die echten Root-Pfade: Login, Refresh, Password-Reset,
      Invite-Accept, Public-Registrierung, Superadmin-Controller, `StandaloneDataInitializer`,
      `ZombieCompanyCleanupJob`.
- [x] `PresenceService.onConnected/onDisconnected` (`PresenceService.kt:28,43`) mit abdecken.

`TenantIdentifierResolver.resolveCurrentTenantIdentifier()` liefert `__root__`, sobald `TenantContext`
leer ist. Jeder Pfad, der den Tenant zu setzen vergisst, sieht stumm alle Tenants. Die im Abschnitt
[Wer setzt den Tenant](#wer-setzt-den-tenant) formulierte "Tenant-Pflicht-Regel im Review" ist keine
technische Kontrolle.

Der Fall existiert im Branch bereits: die Presence-Listener laufen auf STOMP-Event-Threads außerhalb
von `WebSocketAuthInterceptor.beforeHandle`, `conversationRepo.findPartnerIds` läuft dort ungefiltert.
Heute folgenlos (Query ist user-scoped), aber der Beweis, dass die Default-Richtung falsch steht.

### B3. Frische Installation startet in keiner der beiden Betriebsarten

**a) On-Prem ignoriert die `app.standalone.*`-Properties**

- [x] v0.27 Changeset `1775100000000-1` (Default-Company) unter `<preConditions onFail="MARK_RAN">`
      mit Rowcount auf die zu backfillenden Tabellen stellen.

v0.27 legt "Default Company" bedingungslos an. `StandaloneDataInitializer.ensureCompany():51` macht
`findAll().firstOrNull()` und greift damit immer diese Zeile ab. Bei Neuinstallation werden
`company-name/street/zip/city/country` nie benutzt, das `check(missing.isEmpty())` auf `:61` ist toter
Code. Der Kunde bekommt einen Tenant "Default Company" mit Adresse "Unknown".

**b) SaaS-Registrierung antwortet 404**

- [x] Rollen `ADMIN`, `PROJECTMANAGER`, `EMPLOYER` in einer Migration seeden.
- [x] `StandaloneDataInitializer.ensureRoles()` danach löschen.

Diese drei Rollen seedet keine Migration — nur `ensureRoles()`, und der Bean hängt an
`@ConditionalOnProperty(app.standalone.enabled=true)`. Default ist `false`. Auf frischer SaaS-DB läuft
`POST /api/public/companies/register` in `roleRepository.findByName("ADMIN") == null` und wirft
`ROLE_NOT_FOUND`. Dass jeder Integrationstest die Rolle von Hand anlegen muss
(`CompanyRegistrationIT:26`, `SuperadminCompanyControllerIT:29`), war der Hinweis darauf.

**c) Kein Weg zum ersten SUPERADMIN**

- [x] Entscheiden: Bootstrap-Property analog `app.standalone.*` oder dokumentierter SQL-Schritt.

Rollenzeile, Security-Regel, Controller und Tests existieren, aber kein Produktpfad legt den Account
an. `/api/superadmin/**` ist in einer echten Umgebung unerreichbar; die Tests bauen den User per
Repository.

## Sollte im selben Durchgang mit

### Enforcement

- [x] **`isEnabled` fällt offen auf.** `CompanyService.kt:37` wertet eine unbekannte Company als aktiv.
      Der Lookup ist autoritativ und `setEnabled` hat `@CacheEvict` — die Begründung im Kommentar
      trägt nicht. Eine vom Zombie-Job gelöschte Company passiert das Gate. → `.orElse(false)`.
- [x] **Deaktivierte Company nur über HTTP gesperrt.** `WebSocketAuthInterceptor.authenticate` prüft
      `isEnabled` nicht; gesperrter Tenant behält Chat, Presence und Notifications über STOMP.
- [x] **`setEnabled(false)` beendet keine Sessions.** Weder Refresh-Token-Revoke noch
      `sessionRegistry.disconnect`, obwohl `AdminUserService.updateUserStatus:90-93` für einen
      einzelnen User genau das tut. Gleiche Semantik, zwei Standards.
- [x] **`COMPANY_ENABLED` ist prozesslokal.** Caffeine mit 60s TTL, `@CacheEvict` wirkt nur auf einer
      Instanz. Bei mehr als einer Instanz arbeitet ein gesperrter Tenant bis zu 60s weiter. Mindestens
      dokumentieren, siehe [Querschnitt](#querschnitt-was-mitdenken).

### Struktur

- [x] **`CompanyService.create` und `CompanyRegistrationService.register` sind dieselben ~50 Zeilen
      zweimal** (Namensprüfung, Rollen-Lookup, Company, Admin, Invite, Audit). Unterschied sind
      `isEnabled`/`selfRegistered` und der Duplicate-Email-Zweig. → eine Methode mit Parameter.
- [x] **Company-Aktivierung liegt im falschen Service.** `InvitationService.activateSelfRegisteredCompany:176`
      zieht `CompanyRepository`, `CacheManager` und `AuditService` in den Invitation-Service und baut
      die Cache-Eviction von Hand nach, die `CompanyService.setEnabled` deklarativ schon hat.
- [x] **`SuperadminCompanyController` ohne `@PreAuthorize`.** Verlässt sich allein auf die Pfadregel in
      `SecurityConfig:116`. Jeder andere Controller im Repo setzt es zusätzlich — Hausregel aus CLAUDE.md.
- [x] **`CreateCompanyRequest` bedient public und Superadmin-Endpunkt.** Ein später ergänztes Feld wird
      damit versehentlich öffentlich setzbar. → trennen.

### Daten & Betrieb

- [x] **Registrierung leakt Firmennamen.** `CompanyRegistrationService:37` wirft 409 mit dem Namen,
      während `existsByEmail` bewusst 202 liefert. Kundenliste ist enumerierbar. Entweder bewusst
      akzeptieren und hier notieren, oder auch bei Namenskollision 202 antworten.
- [x] **Tote Spalten.** v0.27 gibt `refresh_tokens` und `password_reset_tokens` ein `company_id`, das
      keine Entity mappt: bleibt NULL, kein FK, kein Index. → droppen.
- [x] **`ZombieCompanyCleanupJob` ohne Lock.** Bei mehreren Instanzen löschen um 04:00 alle Knoten
      dasselbe. Läuft außerdem im Standalone-Modus, wo er nie treffen kann.

## Kleinkram

- [x] `TenantAwareEntity.companyId: String?` ist in Kotlin nullable, in der DB `NOT NULL`. Alles
      danach null-behandelt ein Feld, das nicht null sein kann.
- [x] `country` nur auf Länge 2 validiert, kein ISO-3166. `@Pattern("[A-Z]{2}")` plus uppercase.
- [x] `mail.smtp.enabled=false` in `application-test.properties` gehört nicht in dieses Feature.

## Testlücken

- [x] `TenantIsolationIT` testet Repositories direkt. Der [Testplan](#testplan) verlangt den
      Service-Layer.
- [x] Kein Test beweist, dass Tenant A mit echtem JWT über einen Controller nicht an Tenant Bs
      Projekt/User/Chat kommt. Genau dieser Test hätte ein fehlendes `beforeHandle` gefangen.
- [x] Kein Test für den Root-Kontext der Presence-Listener (siehe B2).

## Bewusst so gelassen — nicht "vergessen"

Positiv im Review bestätigt, nicht anfassen ohne Grund:

- `@TenantId` statt handgeschriebenem `WHERE company_id` in jedem Repository.
- `JwtAuthenticationFilter:32,69` sichert den vorherigen Tenant und stellt ihn wieder her, statt blind
  zu clearen — und cleart *vor* dem Claim-Lesen, damit kein Request einen Ambient-Tenant erbt.
- `ExecutorChannelInterceptor.beforeHandle` statt `preSend` für den STOMP-Pfad.
- Rabbit-Envelope mit `companyId` plus `@JsonTypeInfo` auf dem verschachtelten Sealed Type.
- Cache-Keys mit Tenant-Präfix inklusive `CacheTenantIT`.
- Migrationen in der Reihenfolge nullable → backfill → constrain.
