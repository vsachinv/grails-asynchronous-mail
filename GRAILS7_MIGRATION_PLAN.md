# Grails 7 Migration Plan — asynchronous-mail plugin

**Scope:** Grails 6.1.0 / Java 11 / Spring Boot 2.7 → Apache Grails 7.0.16 / Java 17 / Spring Boot 3.5.16 / Jakarta EE
**Branch:** `task/7.x-upgrade`
**Rollback point:** commit `3c4075b` (pre-flight tag check was skipped at the user's request; no `pre-grails7-migration` tag exists)
**Plan date:** 2026-09-16
**Status:** APPLIED — all changes made, unit + integration tests green, published to mavenLocal. Not committed.

---

## 1. Detected baseline and chosen target

| Item | Current | Target | Basis for the choice |
|---|---|---|---|
| Grails | 6.1.0 | **7.0.16** | `mail-oauth 7.6.0-M1` (already in `~/.m2` and on the internal Nexus) is built against `grails-bom 7.0.16`. Grails 7.0.x ships Hibernate 5.6, so no Hibernate 6 changes apply. |
| Java | 11 | **17** | Grails 7 minimum. SDKMAN `17.0.6-zulu` is the current default on this machine. |
| Gradle | 7.6.3 | **8.14.4** | Grails 7 Gradle plugin requires Gradle 8. |
| Spring Boot | 2.7.x | **3.5.16** | Managed by `grails-bom 7.0.16`. |
| Groovy | 3.0.x | **4.0.33** | Managed by the BOM. |
| Plugin version | `7.5-JDK11-1.0-M14` | **`7.6.0-M1`** | Confirmed by the user. Aligns with the mail-oauth fork's Grails 7 line (not the generic `7.0.0-M1`). |
| Group / artifact | `io.github.gpc:asynchronous-mail` | unchanged | |

### Third-party plugin coordinates

| Plugin | Current | Target | Notes |
|---|---|---|---|
| mail | `org.grails.plugins:mail:4.0.0` | `org.grails.plugins:grails-mail:5.0.3` | Confirmed by the user. Renamed artifact. Plugin name stays `mail`, declares `grailsVersion 7.0.0 > *`, uses `jakarta.mail`. The Apache `org.apache.grails:grails-mail` artifact only exists as 8.0.0 milestones and must not be used. |
| mail-oauth (RxLogix fork) | `org.grails.plugins:mail-oauth:7.5-JDK11-1.0-M12` | `org.grails.plugins:mail-oauth:7.6.0-M1` | Confirmed by the user. Plugin name `mailOauth`, `dependsOn mail > 5.0.0`, provides `grails.plugins.mail.oauth.TenantMailService`. |
| quartz | `org.grails.plugins:quartz:2.0.13` + `org.quartz-scheduler:quartz:2.3.2` | `org.apache.grails:grails-quartz:7.0.16` (+ quartz 2.5.2) | Final choice by the user (superseding an interim 4.0.1). Same version as the BOM manages. Plugin name stays `quartz`. |
| hibernate5 | `org.grails.plugins:hibernate5` + pinned `hibernate-core:5.6.15.Final` | `org.apache.grails:grails-data-hibernate5` (BOM) | Drop the explicit hibernate-core pin and the `groovy-xml:3.0.13` force. |
| async | `org.grails.plugins:async` | `org.apache.grails:grails-async` (BOM) | Used by `AsynchronousMailProcessService` (`grails.async.Promises`). |
| scaffolding | `org.grails.plugins:scaffolding` | `org.apache.grails:grails-scaffolding` (BOM) | Keep for now; only needed by the shipped scaffolding templates. |
| gsp | `org.grails.plugins:gsp` | `org.apache.grails:grails-gsp` + `org.apache.grails.views:grails-web-gsp` (BOM) | Needed by `MailMessageContentRenderer`. |
| asset-pipeline | `com.bertramlabs.plugins:asset-pipeline-gradle:4.4.0` | `cloud.wondrify:asset-pipeline-gradle:5.0.34` | Kept because the scaffolding GSP templates use `<asset:...>` tags and `grails-app/assets/stylesheets/asyncmail.css` exists. Requires the empty-jar packaging fix (section 3.1). |
| git-properties | `com.gorylenko.gradle-git-properties:2.4.2` (buildSrc) | `4.0.1` (buildscript classpath) | |
| micronaut-inject-groovy | `compileOnly` / `testImplementation` | removed | Not used by the plugin; Grails 7 no longer needs it. |

### Libraries Grails 7 no longer provides transitively

| Library | Where used | Action |
|---|---|---|
| `commons-validator` | `src/main/java/.../Validator.java` (`EmailValidator`) | Declare explicitly (`implementation 'commons-validator:commons-validator'`). Pre-existing dependency, previously transitive via GORM validation. |
| `commons-lang` 2 | one `StringUtils.isBlank` call in `AsynchronousMailMessage` headers validator | Replace with a plain Groovy null/trim check and drop the import. No behaviour change. |

---

## 2. Scan results

### javax → jakarta occurrences (complete list)

| File | Import | Replacement |
|---|---|---|
| `src/main/java/grails/plugin/asyncmail/Validator.java` | `javax.mail.internet.AddressException`, `javax.mail.internet.InternetAddress` | `jakarta.mail.internet.*` |
| `src/main/groovy/grails/plugin/asyncmail/AsynchronousMailMessageBuilder.groovy` | `javax.activation.FileTypeMap` | `jakarta.activation.FileTypeMap` |
| `src/main/groovy/grails/plugin/asyncmail/AsynchronousMailMessageBuilderFactory.groovy` | `javax.activation.FileTypeMap`, `javax.activation.MimetypesFileTypeMap` | `jakarta.activation.*` |
| `src/integration-test/groovy/grails/plugin/asyncmail/AsyncMailServiceSpec.groovy` | `javax.annotation.Resource` | `jakarta.annotation.Resource` |

No JDK `javax.*` packages (crypto, net, xml, sql) are used, so a blanket replace is safe.

### GORM 9 audit

- No HQL positional parameters (`?0`).
- The one `executeUpdate` (expired-message bulk update in `AsynchronousMailPersistenceService`) is an `update ... set` statement with named parameters, which is valid.
- No `blank: true` constraints without `nullable: true`, so the empty-string-to-null coercion does not break validation.
- Criteria queries (`withCriteria`, `projections { property('id') }`) are unchanged in GORM 9.

### Removed Grails 6 APIs

None found. No `GrailsWebMockUtil`, `ServletContextHolder`, `ClassRelativeResourcePatternResolver`, servlet filters, interceptors, or Spring Security.

### Mail plugin API compatibility (verified against `grails-mail-5.0.3.jar`)

- `MailMessageContentRenderer.render(Writer, String, Map, Locale, String)` still exists with the same signature used by `AsynchronousMailMessageBuilder.doRender`.
- `MailMessageBuilder`, `MailMessageContentRender`, `GrailsMailException`, `MailService` are all still in package `grails.plugins.mail`.
- `MailMessageBuilder` constructor now takes `MailConfigurationProperties`, but this plugin does not construct it directly; `CompareMessageBuilderSpec` only compares method names.

### Quartz plugin API compatibility (verified against `grails-quartz-4.0.1.jar`)

`grails.plugins.quartz.JobManagerService`, `JobDescriptor`, `TriggerDescriptor` used by `AsynchronousMailGrailsPlugin.startJobs` are present. Plugin name remains `quartz`.

---

## 3. Mechanical changes (safe, applied without further approval)

| # | File | Change |
|---|---|---|
| M1 | `build.gradle` | Rewrite to the `buildscript {}` style. Details in 3.1. |
| M2 | `buildSrc/` | Delete the directory. Its role moves to the `buildscript {}` classpath. |
| M3 | `gradle.properties` | `grailsVersion=7.0.16`, `version=7.6.0-M1`, `springBootVersion=3.5.16`, add `org.gradle.caching=true`. |
| M4 | `settings.gradle` | Reduce to `rootProject.name = 'asynchronous-mail'`. |
| M5 | `gradle/wrapper/gradle-wrapper.properties`, `gradle-wrapper.jar`, `gradlew`, `gradlew.bat` | Regenerate for Gradle 8.14.4 with `./gradlew wrapper --gradle-version 8.14.4 --distribution-type bin` (run twice). |
| M6 | `src/main/groovy/.../AsynchronousMailGrailsPlugin.groovy` | `grailsVersion = "7.0.0 > *"`, `dependsOn = [mail: "* > 5.0.0"]`, add `'mailOauth'` to `loadAfter`. |
| M7 | `src/main/java/.../Validator.java` | javax → jakarta mail imports. |
| M8 | `AsynchronousMailMessageBuilder.groovy`, `AsynchronousMailMessageBuilderFactory.groovy` | javax → jakarta activation imports. |
| M9 | `src/integration-test/.../AsyncMailServiceSpec.groovy` | javax → jakarta `@Resource`. |
| M10 | `grails-app/domain/.../AsynchronousMailMessage.groovy` | Replace `StringUtils.isBlank(x)` with `!x?.trim()` and drop the `org.apache.commons.lang` import. |
| M11 | `.github/workflows/build.yml`, `gradle-github-publish.yml`, `release.yml` | JDK 11 / 8 → 17. |
| M12 | `README.md` | Add the Grails 7 installation coordinates (`io.github.gpc:asynchronous-mail:7.6.0-M1`). |
| M13 | `CLAUDE.md` | Update the stack description after the upgrade lands. |

### 3.1 `build.gradle` target shape

```groovy
buildscript {
    repositories {
        mavenLocal()
        mavenCentral()
        maven { url = 'https://repo.grails.org/grails/restricted' }
    }
    dependencies {
        classpath platform("org.apache.grails:grails-bom:$grailsVersion")
        classpath "org.apache.grails:grails-gradle-plugins"
        classpath "cloud.wondrify:asset-pipeline-gradle:5.0.34"
        classpath "com.gorylenko.gradle-git-properties:gradle-git-properties:4.0.1"
        classpath "io.github.gradle-nexus:publish-plugin:1.3.0"
    }
}

apply plugin: 'eclipse'
apply plugin: 'idea'
apply plugin: 'org.apache.grails.gradle.grails-plugin'
apply plugin: 'cloud.wondrify.asset-pipeline'
apply plugin: 'maven-publish'
apply plugin: 'signing'
apply plugin: 'com.gorylenko.gradle-git-properties'

repositories {
    mavenLocal()
    mavenCentral()
    maven { url = 'https://repo.grails.org/grails/restricted' }
    maven {                                   // internal Nexus, unchanged URL
        url = '<existing internal Nexus URL>'
        allowInsecureProtocol = true
    }
}

dependencies {
    implementation platform("org.apache.grails:grails-bom:$grailsVersion")

    implementation 'org.apache.grails:grails-core'
    implementation 'org.apache.grails:grails-logging'        // was org.grails:grails-logging
    implementation 'org.apache.grails:grails-services'       // was grails-plugin-services; REQUIRED, registers grails-app/services beans
    implementation 'org.apache.grails:grails-databinding'    // was grails-plugin-databinding
    implementation 'org.apache.grails.i18n:grails-i18n'      // was grails-plugin-i18n
    implementation 'org.apache.grails:grails-controllers'
    implementation 'org.apache.grails:grails-rest-transforms'
    implementation 'org.apache.grails:grails-data-hibernate5'
    implementation 'org.apache.grails:grails-async'
    implementation 'org.apache.grails:grails-scaffolding'
    implementation 'org.apache.grails:grails-gsp'
    implementation 'org.apache.grails.views:grails-web-gsp'
    implementation 'org.apache.grails.views:grails-web-gsp-taglib'

    implementation 'org.grails.plugins:grails-mail:5.0.3'
    implementation 'org.grails.plugins:mail-oauth:7.6.0-M1'
    implementation 'org.apache.grails:grails-quartz:7.0.16'
    implementation 'commons-validator:commons-validator'   // version to be confirmed against BOM / resolved graph

    assets 'org.apache.grails:grails-dependencies-assets'

    testRuntimeOnly 'com.h2database:h2'
    testRuntimeOnly 'org.apache.tomcat:tomcat-jdbc'
    testImplementation 'org.springframework.boot:spring-boot-starter-tomcat'
    testImplementation 'org.apache.grails.testing:grails-testing-support-core'
    testImplementation 'org.apache.grails:grails-testing-support-web'
    testImplementation 'org.apache.grails:grails-testing-support-datamapping'
    testImplementation 'org.spockframework:spock-core'
    testImplementation 'net.bytebuddy:byte-buddy'
    testRuntimeOnly    'net.bytebuddy:byte-buddy-agent'
}

compileJava.options.release = 17

// https://github.com/apache/grails-core/issues/15321
tasks.withType(GroovyCompile).configureEach {
    groovyOptions.optimizationOptions.indy = false
}

tasks.withType(Test).configureEach { useJUnitPlatform() }
bootJar.enabled = false

assets {
    packagePlugin = true
    minifyJs = true
    minifyCss = true
}

// Empty-jar packaging fix: assetPluginPackage otherwise writes into build/resources/main,
// which processResources cleans, so META-INF/assets is lost from the jar.
def packagedAssetsDir = layout.buildDirectory.dir('packagedAssets')
afterEvaluate {
    tasks.named('assetPluginPackage') {
        destinationDirectory = packagedAssetsDir.map { it.dir('META-INF') }
    }
}

jar {
    enabled = true
    archiveClassifier = ''
    dependsOn generateGitProperties, tasks.named('assetPluginPackage')
    manifest { /* unchanged: Plugin-Version, Plugin-Title, Git-Commit, Git-Branch, ... */ }
    from sourceSets.main.output
    from packagedAssetsDir
    exclude 'git.properties'
}

// sourceJar / packageJavadoc / packageGroovydoc: switch `classifier =` to `archiveClassifier =`
// javadoc block: links to Java 17 API docs
// gitProperties, publishing (GitHubPackages + NexusRepo), signing, nexusPublishing (release), snapshotVersion: unchanged
```

Verification for the asset fix: `unzip -l build/libs/*.jar | grep -c 'META-INF/assets/'` must be non-zero on three consecutive clean builds.

---

## 4. Logic changes (each requires explicit approval before it is applied)

| # | File | Change | Why | Risk |
|---|---|---|---|---|
| L1 | withdrawn | Quartz classpath exclusion is not needed with `org.apache.grails:grails-quartz:4.0.1` (no `org.grails` transitive dependencies). | | |
| L2 | `src/test/groovy/.../AsynchronousMailSendServiceSpec.groovy` | Rewrite the two feature methods to mock `TenantMailService.sendMailWithTenant(Long, Closure)` instead of `MailService.sendMail(Closure)` | The spec still targets the pre-multi-tenant service and sets `service.mailService`, which no longer exists. This is a pre-existing gap, not a Grails 7 regression, but it blocks a green verification run. | Low |
| L3 | `src/test/groovy/.../AsynchronousMailMessageBuilderSpec.groovy` | Replace `AsynchronousMailMessageBuilder.metaClass.doRender = {...}` with a Spock `Spy` only if the metaclass stub no longer intercepts the call under Groovy 4 | Proposed conditionally; applied only if the test fails after the mechanical changes. | Low |

---

## 5. Risks and items requiring manual verification

| # | Risk | Mitigation |
|---|---|---|
| R1 | `grails-quartz 4.0.1` is not managed by `grails-bom 7.0.16` and declares `grailsVersion 7.0.0-SNAPSHOT > *` | Run `AsynchronousMailProcessServiceIntegrationSpec` and a host-app smoke test that both jobs get scheduled (`asynchronous.mail.disable=false`). The explicit dependency pins quartz-scheduler 2.5.2. |
| R2 | `src/main/scripts/CreateAsynchronousMailController.groovy` uses the Grails 3–6 code-generation script DSL | Unknown whether the Grails 7 CLI still executes plugin-supplied scripts. Leave in place; verify `grails create-asynchronous-mail-controller` in a Grails 7 host app. If unsupported, document manual copy of the templates. |
| R3 | Oracle VPD context call (`PKG_MART_SET_CONTEXT`) via `session.createSQLQuery` | Hibernate 5.6 is retained in Grails 7.0.x, so the API is unchanged. Re-check when moving to Grails 7.1 / Hibernate 6 (`createNativeQuery`). |
| R4 | `mail-oauth 7.6.0-M1` resolves only from `mavenLocal()` on this machine and from the internal Nexus in CI | Keep both repositories. Do not publish the plugin until mail-oauth 7.6.0-M1 is confirmed on Nexus (it is listed there as of this scan). |
| R5 | GitHub Actions workflows publish to Sonatype using JDK 8 | Bump to 17 (M11). The Sonatype flow itself is unchanged. |
| R6 | Consuming application must also be on Grails 7 | This plugin cannot be dropped into a Grails 6 application after this change. Coordinate with the application upgrade. |

Policy note: no authentication, cryptography, payment, or PII-handling code is modified by this plan. Mail addresses are stored as before; no schema change is introduced.

---

## 6. Files with no changes needed

- `grails-app/domain/**` except the `StringUtils` swap in `AsynchronousMailMessage`
- `grails-app/services/**` (config, persistence, process, send services)
- `grails-app/jobs/**`
- `grails-app/conf/application.yml`, `grails-app/conf/plugin.groovy`
- `grails-app/init/.../Application.groovy`
- `src/main/groovy/.../AsynchronousMailService.groovy`, `enums/MessageStatus.groovy`
- `src/main/templates/**`, `src/main/scripts/**` (pending R2)
- `db/changelog/**`
- `src/test/resources/application-test.yml`

---

## 7. Execution order and verification

1. Apply M1–M4, then M5 (wrapper regeneration needs a working build file).
2. `./gradlew --version` → Gradle 8.14.4 on JDK 17.
3. Apply M6–M10.
4. `./gradlew clean compileGroovy compileJava` → confirms quartz linkage and the `commons-validator` version.
5. Present L2–L3 for approval; apply approved ones.
6. `./gradlew test`
7. `./gradlew integrationTest`
8. `./gradlew assemble` and the three-run asset-count check.
9. `./gradlew publishToMavenLocal`; install into a Grails 7 host application; verify job scheduling, an immediate send, an attachment send, and R2.
10. Apply M11–M13.
11. Report results. Nothing is committed unless explicitly requested.

## 8. Approval log

| Item | Decision | Date |
|---|---|---|
| Pre-flight tag check | skipped by user | 2026-09-16 |
| Versions: mail 5.0.3, quartz `org.apache.grails:grails-quartz:4.0.1`, mail-oauth 7.6.0-M1, plugin version 7.6.0-M1 | confirmed by user | 2026-09-16 |
| Mechanical changes M1–M13 | applied (`go all`) | 2026-09-16 |
| L1 | withdrawn (quartz 4.0.1 chosen by user) | 2026-09-16 |
| L2 | applied | 2026-09-16 |
| L3 | not needed | 2026-09-16 |

---

## 9. Execution record (2026-09-16)

### Applied
- M1–M13 as planned, with these additions discovered during verification:
  - `build.gradle`: added `grails-logging`, `grails-services`, `grails-databinding`, `grails-i18n` (Grails 7 names of the four core plugins the Grails 6 build declared). Without `grails-services` no `grails-app/services` bean is registered and the context fails on `mailOAuthService`.
  - `build.gradle`: `sourceJar` now depends on `generateGitProperties` and excludes `git.properties` (Gradle 8 implicit-dependency validation).
  - `build.gradle`: Nexus publish URL is only set when `nexusUrl` / `NEXUS_URL` is present (Gradle 8 rejects a null URL).
  - `grails-app/init/.../Application.groovy` (`@PluginSource`, test context only): defines `mailService` pinned to the default `mailMessageBuilderFactory` (grails-mail 5.x constructor-injects a `MailMessageBuilderFactory`; mail-oauth registers three) and a stub `tenantContextProvider` required by mail-oauth's `tenantMailService`. A `@Configuration` class under `src/integration-test` was tried first and is not component-scanned, so it was discarded.
  - `src/integration-test/.../H2VpdContextStub.groovy` + `application-test.yml` `INIT` clause: registers `PKG_MART_SET_CONTEXT.SET_CONTEXT` in H2 so `setVPDContextForAllTenantAccess()` succeeds on H2. No production code changed.
  - Integration fixtures now set `tenantId: 1L` / `tenantId 1L` (NOT NULL `tenant_id`).
- L2 applied: `AsynchronousMailSendServiceSpec` mocks `TenantMailService.sendMailWithTenant`.
- L3 not needed: builder spec passed unchanged under Groovy 4.
- Unit test fixes (pre-existing failures, all also failing on the Grails 6 baseline): `AsynchronousMailMessageSpec` now includes the message class in `getDomainClassesToMock` and the `toString` expectation matches `@ToString` output; `AsynchronousMailProcessServiceSpec` fixture sets `tenantId`.

### Baseline comparison
Grails 6 HEAD (`3c4075b`) run in a throwaway worktree on JDK 17: unit 39 run / 18 failed, integration 22 run / 22 failed. The same 18 + 22 were the failures seen after the upgrade before any test fix, so none were Grails 7 regressions.

### Verification results
| Step | Result |
|---|---|
| `./gradlew clean compileGroovy compileJava` | OK |
| `./gradlew test` | 39 tests, 0 failed |
| `./gradlew integrationTest` | 22 tests, 0 failed |
| `./gradlew clean build` x3 with asset count | OK each run, 2 `META-INF/assets/` entries each time |
| `./gradlew publishToMavenLocal` | OK, `io.github.gpc:asynchronous-mail:7.6.0-M1` in `~/.m2`, POM shows grails-bom 7.0.16, grails-mail 5.0.3, mail-oauth 7.6.0-M1, grails-quartz 7.0.16 |
| `./gradlew --version` | Gradle 8.14.4 on JDK 17.0.6 |

### Still to verify manually (see R2, R6)
- `grails create-asynchronous-mail-controller` in a Grails 7 host app (legacy script DSL).
- Host app smoke test: both Quartz jobs scheduled, immediate send, attachment send, VPD behaviour on Oracle.
- Consuming applications must themselves resolve the `MailMessageBuilderFactory` ambiguity and provide `tenantContextProvider` (as the test `Application` does here).

### Security note
The `mail-oauth-7.6.0-M1-sources.jar` in the local Maven repository contains an `application.yml` with what appear to be real OAuth client credentials. Not copied anywhere by this migration; reported to the owner for rotation / removal from the published artifact.
