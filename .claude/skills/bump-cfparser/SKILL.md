---
name: bump-cfparser
description: Upgrade CFLint's cfparser (cfml.parsing) dependency to a new version and verify it actually took effect. Use this whenever someone wants to pick up parser changes, a grammar fix, or new cfparser behaviour, whenever a cfparser bug was fixed upstream and needs pulling in, and whenever a parser-level fix "isn't working" in CFLint — that is usually an unpublished artifact, a half-applied bump, or a cached SNAPSHOT rather than a real bug.
---

# Bumping the cfparser dependency

This looks like a one-line change and is not. Three separate things make a bump appear to work
while CFLint still runs the old parser.

## 1. The version is declared twice

Both must change:

| File | Form |
|---|---|
| `pom.xml` | `<cfparser.version>X.Y.Z-SNAPSHOT</cfparser.version>` property |
| `build.gradle` | hardcoded `implementation 'com.github.cfmleditor:cfml.parsing:X.Y.Z-SNAPSHOT'` |

Gradle is the primary build and what CI runs, so a Maven-only bump is the worse half to miss —
tests pass locally under Maven while CI keeps compiling against the old artifact.

Confirm both moved:

```bash
grep -rn "cfml\.parsing\|cfparser\.version" pom.xml build.gradle
```

Grep the full coordinate rather than a bare version number. `commons-io:2.15.1` in `build.gradle`
matches a naive `2\.15\.` search and is unrelated, and the tracked `pom.xml.versionsBackup` still
pins a long-dead `2.6.0` under the old `com.github.cfparser` groupId — no build reads it, but it
is a decoy every time someone greps for versions here.

`CHANGELOG.md` needs no edit: it is generated from git history by the `gitChangelog` Gradle task,
so a hand-written entry is overwritten on the next run.

## 2. The new version has to actually exist

cfparser publishes to GitHub Packages **only** on a tag push, a release, or a manual workflow
dispatch — never on merge to `master`. A fix merged upstream is not automatically available here.

Before bumping, confirm the coordinate was really published: check that cfparser's *Maven Package*
workflow ran green for that version, not merely that the commit is on `master`. A tag existing is
not sufficient either — a publish run can fail after the tag is created, leaving a version that
looks released and was never uploaded.

If it has not been published, the bump will fail to resolve, and the honest move is to say so and
get it published first rather than pinning something unresolvable.

## 3. Check the Java baseline moved too

cfparser and CFLint must stay on the same Java baseline. If cfparser raised its `maven.compiler.release`,
CFLint's artifacts cannot load the newer class file version and will fail at runtime with
`UnsupportedClassVersionError` — even though compilation succeeds, because `javac` happily reads
class files newer than its own `-target`.

Four places carry the Java version here:

- `pom.xml` — `maven.compiler.source` / `maven.compiler.target`
- `build.gradle` — toolchain `languageVersion`
- `.github/workflows/gradle.yml` and `publish.yml` — runner `java-version`

`native-release.yml` runs a newer JDK for GraalVM and is usually already ahead.

## 4. Verify

```bash
mvn clean test        # 675 tests
./gradlew build
```

Prefer `mvn clean test` when checking a fresh upstream artifact. It resolves independently of
Gradle's cache and gives a trustworthy answer.

**Read a resolution failure carefully — it probably names the wrong repository.** The
`allow-snapshots` profile in `pom.xml` is `activeByDefault` and adds Sonatype's snapshot
repository, which Maven tries *before* the `github` repository that actually hosts cfparser. So an
artifact that simply has not been published yet surfaces as a Sonatype error, and a network or
proxy problem surfaces the same way. Neither message means what it appears to.

To tell "not published" apart from "cannot reach the repository", re-resolve pinning a version you
know exists:

```bash
mvn dependency:get -Dartifact=com.github.cfmleditor:cfml.parsing:2.15.2-SNAPSHOT
```

If the known-good version fails identically, the environment is the problem and the run says
nothing about publication — go back to checking the upstream workflow run instead.

The Gradle build may not run in every environment: the toolchain pins `JvmVendorSpec.ADOPTIUM`, so
a non-Temurin JDK fails unless the foojay resolver can download one. That is an environment
limitation, not a defect — say so rather than reporting the change as broken.

## 5. Do not trust a green CI run too quickly

Gradle treats `-SNAPSHOT` as a changing module and caches it for **24 hours**, and the workflows
restore a Gradle cache. A CI run started shortly after an upstream republish can compile against
the previous artifact and pass, telling you nothing about the version you just pinned.

Ways to get a trustworthy answer:

- A push-triggered run on a different branch, whose cache key differs
- A Maven-resolved build
- Simply waiting out the 24-hour window

When reporting results, distinguish "CI is green" from "CI exercised the new artifact". They are
often not the same claim, and conflating them has hidden a stale dependency here before.
