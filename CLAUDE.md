# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A Grails **plugin** (not an application) that sends email asynchronously: `sendMail {}` persists an
`AsynchronousMailMessage` to the DB and returns immediately; a Quartz job later picks pending messages up
and sends them through the `grails-mail` / `mail-oauth` plugins. This is RxLogix's fork of `gpc/grails-asynchronous-mail`
(group `io.github.gpc`, artifact `asynchronous-mail`) with multi-tenant (MT) and Oracle VPD additions that do not
exist upstream. The plugin version tracks the internal `mail-oauth` fork line (`7.6.0-Mx`), not upstream semver.

Stack on this branch: Apache Grails 7.0.16, Java 17, Spring Boot 3.5, Groovy 4, Hibernate 5.6 (GORM 9), Gradle 8.14,
Jakarta EE. Plugin dependencies: `org.apache.grails:grails-quartz:7.0.16`, `org.apache.grails:grails-mail` (BOM, 7.0.16),
`org.grails.plugins:mail-oauth:7.6.0-M2` (also depends on the first-party grails-mail) (internal fork, resolved from `mavenLocal()` or the internal Nexus).
The `6.x-*` branches are the Grails 6 / Java 11 line. `GRAILS7_MIGRATION_PLAN.md` records the 6 -> 7 migration,
its version decisions and the verification baseline.

## Commands

```bash
./gradlew build                 # compile + unit + integration tests + jar (what CI runs)
./gradlew test                  # unit tests only (src/test/groovy)
./gradlew integrationTest       # integration tests only (src/integration-test/groovy, H2 in-memory)
./gradlew clean build

# single spec / single feature method
./gradlew test --tests 'grails.plugin.asyncmail.AsynchronousMailProcessServiceSpec'
./gradlew integrationTest --tests 'grails.plugin.asyncmail.AsynchronousMailPersistenceServiceSpec'
./gradlew test --tests '*MessageBuilderSpec.Build message*'

./gradlew publishToMavenLocal   # install locally to test against a consuming app
./gradlew publish               # publishes to GitHubPackages + NexusRepo (needs creds via env/props)
./gradlew snapshotVersion       # appends -SNAPSHOT to version in gradle.properties

unzip -l build/libs/*.jar | grep META-INF/assets   # must list asyncmail.css after every build
```

Needs JDK 17 on the path (SDKMAN `17.0.6-zulu` locally). `bootJar` is disabled; the artifact is the plain `jar`, whose
manifest embeds git properties, so a `.git` directory is required (`failOnNoGitDirectory = true`). Gradle worktrees
therefore need `-x generateGitProperties`.

## Build layout

- Build plugins come from the `buildscript {}` block at the top of `build.gradle` (Apache Grails gradle plugins via
  `grails-bom`, `cloud.wondrify` asset-pipeline 5.x, git-properties, nexus publish). There is no `buildSrc`, and
  `settings.gradle` is only the project name. Grails is bumped through `grailsVersion` in `gradle.properties`.
- Framework dependencies are BOM-managed and declared without versions. Coordinates use three groups:
  `org.apache.grails` (core, `grails-services`, `grails-databinding`, `grails-logging`, `grails-data-hibernate5`,
  `grails-async`, `grails-scaffolding`, `grails-gsp`), `org.apache.grails.views` (GSP), `org.apache.grails.i18n`.
  `grails-services` is mandatory: without it no `grails-app/services` class becomes a bean.
- `commons-validator` is declared explicitly (used by `Validator.java`); Grails 7 no longer brings it transitively.
  Do not reintroduce `commons-lang` 2.
- The scaffolding GSPs use `<asset:>` tags, so the asset pipeline stays. `build.gradle` redirects `assetPluginPackage`
  into `build/packagedAssets` (inside `afterEvaluate`) and feeds that into `jar`; otherwise `processResources` wipes
  `META-INF/assets` and the jar ships without CSS. Keep that block intact.
- `sourceJar` depends on `generateGitProperties` and excludes `git.properties` (Gradle 8 task-dependency validation).

## Testing notes

- JUnit Platform + Spock 2.3 (Groovy 4). `net.bytebuddy:byte-buddy` is declared because Spock needs it to mock
  concrete classes such as services.
- Unit specs using `DomainUnitTest<T>` that override `getDomainClassesToMock()` **must include `T` itself** in the
  returned list; on Grails 7 an omitted class validates nothing and every constraint test silently passes.
- Integration tests run against H2 (`src/test/resources/application-test.yml`, `dbCreate: update`, Quartz RAM store).
  The datasource URL's `INIT` clause registers `PKG_MART_SET_CONTEXT.SET_CONTEXT` as an alias for
  `H2VpdContextStub` (`src/integration-test`) so the Oracle VPD call succeeds. Keep the stub if you touch that URL.
- The test `Application` in `grails-app/init` (`@PluginSource`, not bootstrapped by host apps) defines two beans a host
  app would otherwise provide for mail-oauth: `mailService` pinned to the default `mailMessageBuilderFactory`
  (grails-mail 7.x constructor-injects the factory and mail-oauth registers three), and a stub `tenantContextProvider`.
  A `@Configuration` class under `src/integration-test` is not component-scanned, so it does not work as an alternative.
- Every table has a NOT NULL `tenant_id`, so every fixture must set `tenantId` (`tenantId: 1L` on domain objects,
  `tenantId 1L` inside `sendMail {}` closures).

## Architecture

Send path (all in package `grails.plugin.asyncmail`):

1. `AsynchronousMailService` (src/main/groovy, registered as a Spring bean in `AsynchronousMailGrailsPlugin.doWithSpring`,
   alias `asyncMailService`) runs the user's closure against an `AsynchronousMailMessageBuilder` (DSL: `to`, `subject`,
   `html`, `attachBytes`, `tenantId`, plus async extras `beginDate`, `endDate`, `maxAttemptsCount`, `attemptInterval`,
   `delete`, `immediate`, `priority`). The builder is produced by `AsynchronousMailMessageBuilderFactory`, which reads
   the `grails.mail.*` config (`default.from`, `overrideAddress`) and reuses the mail plugin's
   `MailMessageContentRenderer` for GSP-templated bodies.
2. `AsynchronousMailPersistenceService` saves the message. If `send.immediately` is on, `AsynchronousMailJob.triggerNow()`
   fires the Quartz job right away.
3. `AsynchronousMailJob` (grails-app/jobs, `concurrent = false`, group `AsynchronousMail`) calls
   `AsynchronousMailProcessService.findAndSendEmails()`, which selects up to `messages.at.once` ids ordered by
   priority/endDate/attempts, spreads them over `taskPoolSize` `grails.async` tasks (each in its own Hibernate session),
   and runs `processEmailMessage(id)` per message. That method implements the retry state machine on
   `MessageStatus` (`CREATED -> ERROR (provisional) -> SENT | ATTEMPTED | ERROR`), then deletes the message or its
   attachments if `markDelete` / `markDeleteAttachments` is set. `MailParseException`/`MailPreparationException`
   are fatal (no retry); `IllegalState/IllegalArgumentException` are treated as tenant mail-config errors (`ERROR`).
4. `AsynchronousMailSendService` maps the domain object back onto the mail plugin DSL and sends via
   `TenantMailService.sendMailWithTenant(message.tenantId)` from the mail-oauth plugin (not the plain `mailService`).
5. `ExpiredMessagesCollectorJob` periodically bulk-updates `CREATED|ATTEMPTED` rows past `endDate` to `EXPIRED`.

Both jobs have empty static `triggers`; `AsynchronousMailGrailsPlugin.onStartup` unschedules any stale triggers (cluster
safety) and schedules them from `asynchronous.mail.send.repeat.interval` / `expired.collector.repeat.interval`.
Nothing is scheduled when `asynchronous.mail.disable=true`. The descriptor declares `dependsOn = [mail: "* > 7.0.14"]` (first-party grails-mail)
and loads after `mail`, `mailOauth`, `quartz` and the persistence plugins.

Configuration defaults are in `grails-app/conf/plugin.groovy` and read through `AsynchronousMailConfigService`
(the README documents every `asynchronous.mail.*` key). Add new options in both places.

### Domain model and the MT/VPD layer (fork-specific, read before touching persistence)

- `AsynchronousMailMessage` (table `async_mail_mess`) plus child domains `AsynchronousMailTo/Cc/Bcc/Header/Attachment`
  (tables `async_mail_*`, each with its own Oracle sequence). The old `List<String> to/cc/bcc` and `Map headers`
  are preserved as **transient** getters/setters that map onto `toEntries`/`ccEntries`/`bccEntries`/`headerEntries`
  entity collections; keep that public API stable, downstream apps and the scaffolded controller rely on it.
- Every table carries a non-null `tenant_id`; children copy `tenantId` from the parent message in `beforeValidate`.
- The tables sit behind an Oracle VPD policy. `AsynchronousMailPersistenceService.setVPDContextForAllTenantAccess()`
  calls `PKG_MART_SET_CONTEXT.SET_CONTEXT(-99999)` on the *current* session so the background job can see all tenants.
  It is invoked before every read/write in that service and around each worker loop in the process service.
  Any new query path must call it too. It rethrows on failure by design; do not swallow that error in production code.
- Schema changes require a Liquibase changelog under `db/changelog/` (Groovy DSL, guarded with `preConditions`),
  consumed by the downstream application, since the plugin itself uses `dbCreate: update` only in tests.

### Scaffolding

`src/main/scripts/CreateAsynchronousMailController.groovy` implements the `create-asynchronous-mail-controller`
command; it copies `src/main/templates/artifacts/AsynchronousMailController.groovy` and the GSPs under
`src/main/templates/scaffolding/` into the consuming app. Changes to the domain's public API must be mirrored there.
Whether the Grails 7 CLI still executes this legacy script DSL has not been verified in a host app.

## Conventions

- Use `jakarta.*` imports (`jakarta.mail`, `jakarta.activation`, `jakarta.annotation`); no `javax.*` EE packages remain.
- Services in `grails-app/services` are `@Transactional`; `AsynchronousMailService` itself is a plain class wired
  manually in `doWithSpring`, so do not move it to `grails-app/services`.
- Prefer `@CompileStatic` (the send/process/service classes already use it); the persistence service drops it only
  where GORM dynamic criteria are needed.
- Commit messages are prefixed with the Jira key (e.g. `PVCM-128572 : ...`). Do not commit unless explicitly asked.
- `build.gradle` resolves from an internal Nexus mirror and publishes to GitHub Packages and Nexus using credentials
  taken from Gradle properties or `GITHUB_*` / `NEXUS_*` / `SONATYPE_*` / `SIGNING_*` env vars. Never hardcode them.
- Consuming applications must be on Grails 7, must resolve the `MailMessageBuilderFactory` ambiguity themselves and
  must provide a `tenantContextProvider` bean, exactly as the test `Application` does here.
