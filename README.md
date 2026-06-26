# sbt-osv (Open Source Vulnerabilities)

An sbt plugin that scans your project's dependencies against the [OSV (Open Source Vulnerabilities)](https://osv.dev) database and
reports known vulnerabilities.

This is the sister project to [`sbt-dependency-check`](https://github.com/nMoncho/sbt-dependency-check)
offering similar features (we hope to bring feature parity). This project differs a bit from its sister
in the following aspects:
- Doesn't depend on [NVD database](https://nvd.nist.gov/) directly, but on OSV's API (which also aggregates NVD
  information).
- Has more precise vulnerability search, meaning less [false positives](https://dependency-check.github.io/DependencyCheck/general/suppression.html)
  on the analysis report.
- Only supports dependencies from the Maven ecosystem, for now. Whereas DependencyCheck, and in turn `sbt-dependency-check`
  support [way more ecosystems](https://dependency-check.github.io/DependencyCheck/analyzers/index.html).
- By having a simpler design, analysis may be faster than its sister project.

## Installation

Add the plugin to your `project/plugins.sbt`:

```scala
addSbtPlugin("net.nmoncho" % "sbt-osv" % "<version>")
```

The plugin activates automatically for all JVM projects. No additional `enablePlugins(...)` call is required.

## Tasks

### `osvScan`

Runs the dependency vulnerability scan and generates a report.

```
sbt osvScan [arguments...]
```

| Argument                            | Description                                                                                                 |
|-------------------------------------|-------------------------------------------------------------------------------------------------------------|
| `single-report`                     | Produce one combined report for all aggregated sub-projects instead of one report per project.              |
| `all-projects`                      | When used together with `single-report`, gathers dependencies from all projects into a single analysis.     |
| `list-settings`                     | Print the effective `osvScan` settings before running the scan.                                             |
| `list-unused-suppressions`          | After the scan completes, log any suppression rules that did not match any found vulnerability.             |
| `original-summary`                  | Use the DependencyCheck-style summary format (default): lists dependency coordinates and vulnerability IDs. |
| `all-vulnerabilities-summary`       | Summary includes every vulnerability with its CVSS score, whether it breaches the threshold or not.         |
| `offending-vulnerabilities-summary` | Summary includes only vulnerabilities that exceed the `osvFailBuildOnCVSS` threshold.                       |

**Examples:**

```
# Basic scan of all sub-projects
sbt osvScan

# Single combined report, listing any unused suppressions
sbt osvScan single-report list-unused-suppressions

# Scan with a summary that shows only failing vulnerabilities
sbt osvScan offending-vulnerabilities-summary
```

### `osvListSuppressions`

Lists all suppression rules that are active for the project — both those defined inline in `build.sbt` and those
imported from packaged suppressions.

```
sbt osvListSuppressions [argument]
```

| Argument       | Description                                                        |
|----------------|--------------------------------------------------------------------|
| *(none)*       | List suppressions per sub-project (default).                       |
| `per-project`  | Explicit alias for the default: list suppressions per sub-project. |
| `all-projects` | List suppressions aggregated across all projects.                  |

**Examples:**

```
sbt osvListSuppressions
sbt osvListSuppressions all-projects
```


## Settings

### Basic Settings

| Setting                    | Type                   | Default       | Description                                                                                                                                                                                                                  |
|----------------------------|------------------------|---------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `osvFailBuildOnCVSS`       | `Double`               | `11.0`        | Fail the build if any vulnerability has a CVSS score above this threshold. The CVSS scale is 0–10, so the default of `11.0` means the build never fails. See [NVD CVSS](https://nvd.nist.gov/vuln-metrics/cvss) for details. |
| `osvSkip`                  | `Boolean`              | `false`       | Skip this project during the vulnerability scan.                                                                                                                                                                             |
| `osvOutputDirectory`       | `File`                 | `crossTarget` | Directory where reports are written.                                                                                                                                                                                         |
| `osvReportFormats`         | `Seq[ReportGenerator]` | `Seq(HTML)`   | Report formats to generate. Currently `ReportGenerator.HTML` is supported.                                                                                                                                                   |
| `osvAnalysisTimeout`       | `Option[Duration]`     | `None`        | Maximum time allowed for the analysis.                                                                                                                                                                                       |
| `osvConnectionTimeout`     | `Option[Duration]`     | `None`        | URL connection timeout when downloading external data.                                                                                                                                                                       |
| `osvConnectionReadTimeout` | `Option[Duration]`     | `None`        | URL connection read timeout when downloading external data.                                                                                                                                                                  |

### `osvScopes` — `ScopesSettings`

Controls which dependency scopes are included in the scan.

```scala
osvScopes := ScopesSettings(
  compile  = true,   // default
  test     = false,  // default
  runtime  = true,   // default
  provided = true,   // default
  optional = true    // default
)
```

| Field      | Type      | Default | Description                             |
|------------|-----------|---------|-----------------------------------------|
| `compile`  | `Boolean` | `true`  | Include `Compile` scoped dependencies.  |
| `test`     | `Boolean` | `false` | Include `Test` scoped dependencies.     |
| `runtime`  | `Boolean` | `true`  | Include `Runtime` scoped dependencies.  |
| `provided` | `Boolean` | `true`  | Include `Provided` scoped dependencies. |
| `optional` | `Boolean` | `true`  | Include `Optional` scoped dependencies. |

### `osvEngineSettings` — `EngineSettings`

Controls the scan engine behaviour, including caching and the OSV API endpoint.

```scala
osvEngineSettings := EngineSettings(
  baseUrl       = "https://api.osv.dev",
  cacheEviction = Duration.ofDays(1),
  dataDirectory = None
)
```

| Field           | Type           | Default                 | Description                                                                                                                                                                   |
|-----------------|----------------|-------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `baseUrl`       | `String`       | `"https://api.osv.dev"` | Base URL for the OSV API.                                                                                                                                                     |
| `cacheEviction` | `Duration`     | `1 day`                 | How long a cached API response is considered fresh before querying the API again.                                                                                             |
| `dataDirectory` | `Option[File]` | `None`                  | Where to store the local cache database (`osv.db`). When `None`, the plugin resolves a shared directory based on its own JAR location, so all local projects share one cache. |

### `osvSuppressions` — `SuppressionSettings`

Configures suppression rules to ignore known false positives.

```scala
osvSuppressions := SuppressionSettings(
  file            = file(".osvignore"),
  suppressions    = Set(
    SuppressionRule("CVE-1234-5678", "Not applicable — we don't use the affected code path", "")
  ),
  packagedEnabled = false,
  packagedFilter  = SuppressionSettings.PackagedFilter.BlacklistAll
)
```

| Field             | Type                   | Default        | Description                                                                                                                                                        |
|-------------------|------------------------|----------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `file`            | `File`                 | `.osvignore`   | Path to an `.osvignore` suppression file (see [Suppression Files](#suppression-files)).                                                                            |
| `suppressions`    | `Set[SuppressionRule]` | `Set.empty`    | Inline suppression rules defined directly in `build.sbt`. Each rule has a `name` (vulnerability ID), optional `comments`, and optional `source`.                   |
| `packagedEnabled` | `Boolean`              | `false`        | When `true`, the plugin looks inside dependency JARs for bundled `.osvignore` files and imports their rules (see [Packaged Suppressions](#packaged-suppressions)). |
| `packagedFilter`  | `PackagedFilter`       | `BlacklistAll` | Predicate that decides which dependency JARs are allowed to contribute packaged suppression rules.                                                                 |

#### `PackagedFilter` helpers

| Helper                                  | Description                                                       |
|-----------------------------------------|-------------------------------------------------------------------|
| `PackagedFilter.BlacklistAll`           | Reject all packaged suppressions (default).                       |
| `PackagedFilter.WhitelistAll`           | Accept packaged suppressions from every dependency.               |
| `PackagedFilter.ofGav(pred)`            | Accept JARs where `pred(groupId, artifactId, version)` is `true`. |
| `PackagedFilter.ofFile(pred)`           | Accept JARs where `pred(File)` is `true`.                         |
| `PackagedFilter.ofFilename(pred)`       | Accept JARs where `pred(filename)` is `true`.                     |
| `PackagedFilter.ofFilenameRegex(regex)` | Accept JARs whose filename matches `regex`.                       |



## Suppression Files

Suppressions tell the plugin to ignore specific vulnerabilities. The default file is `.osvignore` in the project root,
configurable via `osvSuppressions`.

Each entry in the file is a block separated by a blank line:

```
# Optional comment describing why this is suppressed
# @source: name-of-the-jar-that-introduced-this-rule (optional)
CVE-1234-5678
```

Rules:
- Lines starting with `#` are comments.
- Lines starting with `# @source:` record where the rule came from.
- The first non-comment line is the vulnerability ID (e.g. a CVE or GHSA identifier).
- Multiple rules can be listed consecutively without blank lines between them.

**Example `.osvignore`:**

```
# Not affected — we don't call the vulnerable API
CVE-2021-44228

# False positive in test scope only
# @source: my-library.jar
GHSA-xxxx-yyyy-zzzz
```

Suppression rules can also be defined inline in `build.sbt` via `SuppressionSettings.suppressions`:

```scala
import net.nmoncho.sbt.osv.SuppressionRule
import net.nmoncho.sbt.osv.settings.SuppressionSettings

osvSuppressions := SuppressionSettings(
  suppressions = Set(
    SuppressionRule(
      name     = "CVE-2021-44228",
      comments = "Not affected — we don't call the vulnerable API",
      source   = ""
    )
  )
)
```

## Packaged Suppressions

Libraries can bundle an `.osvignore` file inside their JAR to ship suppression rules alongside their code.
When `packagedEnabled = true`, the plugin extracts and imports those rules automatically.

Use `packagedFilter` to restrict which JARs may contribute rules:

```scala
osvSuppressions := SuppressionSettings(
  packagedEnabled = true,
  packagedFilter  = SuppressionSettings.PackagedFilter.ofGav { (org, name, _) =>
    org == "com.example" && name.startsWith("my-library")
  }
)
```

To ship suppression rules inside your own library, set `packagedEnabled = true` in that library's build.
The plugin will generate the `.osvignore` resource file from `osvSuppressions.suppressions` automatically at compile time.

## License

This project is licensed under the [MIT License](LICENSE).
