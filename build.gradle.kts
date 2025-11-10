// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
import org.apache.tools.ant.taskdefs.condition.Os
import org.aya.gradle.BuildUtil

plugins {
  java
  `jvm-test-suite`
  `jacoco-report-aggregation`
  idea
  `java-library`
  `maven-publish`
  signing
  alias(libs.plugins.jlink) apply false
  id("com.gradleup.nmcp.aggregation").version("1.2.0")
}

var projectVersion: String by rootProject.ext
var currentPlatform: String by rootProject.ext
var supportedPlatforms: List<String> by rootProject.ext
var javaVersion: Int by rootProject.ext

projectVersion = libs.versions.project.get()
javaVersion = libs.versions.java.get().toInt()

// Platforms we build jlink-ed aya for:
// The "current" means the "current platform", as it is unnecessary to detect what the current system is,
// as calling jlink with default arguments will build for the current platform.
currentPlatform = "current"

// In case we are in CI, or we are debugging CI locally, we build for all platforms
fun buildAllPlatforms(): Boolean {
  if (System.getenv("CI") != null) return true
  if (System.getProperty("user.name").contains("kiva")
    && project.rootDir.resolve(".git/HEAD").readLines().joinToString().contains("refs/heads/ci")) return true
  return false
}
supportedPlatforms = if (!buildAllPlatforms()) listOf(currentPlatform) else listOf(
  "windows-aarch64",
  "windows-x64",
  "linux-aarch64",
  "linux-x64",
  "linux-riscv64",
  "macos-aarch64",
  // disabled for a while because jdk download on GitHub Actions keeps failing
  // "macos-x64",
)

allprojects {
  group = "org.aya-prover"
  version = projectVersion
}

val useJacoco = listOf("base", "syntax", "producer", "pretty", "cli-impl", "jit-compiler", "tools")

/** gradle.properties or environmental variables */
fun propOrEnv(name: String): String =
  if (hasProperty(name)) property(name).toString()
  else System.getenv(name) ?: ""

val isSnapshot = projectVersion.endsWith("SNAPSHOT")
val isRelease = !isSnapshot
subprojects {
  val proj = this@subprojects

  apply {
    plugin("java")
    plugin("idea")
    if (name in useJacoco) plugin("jacoco")
    plugin("maven-publish")
    plugin("java-library")
    plugin("signing")
  }

  java {
    withSourcesJar()
    if (isRelease) withJavadocJar()
    JavaVersion.toVersion(javaVersion).let {
      sourceCompatibility = it
      targetCompatibility = it
    }
    toolchain {
      languageVersion.set(JavaLanguageVersion.of(javaVersion))
    }
  }

  if (name in useJacoco) jacoco {
    toolVersion = rootProject.libs.versions.jacoco.get()
  }

  idea.module {
    outputDir = file("out/production")
    testOutputDir = file("out/test")
  }

  tasks.withType<JavaCompile>().configureEach {
    options.apply {
      isDeprecation = true
      compilerArgs.addAll(listOf("-Xlint:unchecked", "--enable-preview"))
    }

    doLast {
      val root = destinationDirectory.asFile.get()
      // skip for test sources
      if (root.endsWith("test")) return@doLast
      val tree = fileTree(root)
      tree.include("**/*.class")
      tree.include("module-info.class")
      tree.forEach {
        BuildUtil.stripPreview(
          /* root = */ root.toPath(),
          /* classFile = */ it.toPath(),
          /* forceJava21 = */ true,
          /* verbose = */ false,
        )
      }
    }
  }

  tasks.javadoc {
    val options = options as StandardJavadocDocletOptions
    options.modulePath(tasks.compileJava.get().classpath.toList())
    options.addBooleanOption("-enable-preview", true)
    options.addStringOption("-source", javaVersion.toString())
    options.addStringOption("Xdoclint:none", "-quiet")
    options.tags(
      "apiNote:a:API Note:",
      "implSpec:a:Implementation Requirements:",
      "implNote:a:Implementation Note:",
    )
  }

  artifacts {
    add("archives", tasks.named("sourcesJar"))
    if (isRelease) add("archives", tasks.named("javadocJar"))
  }

  if (name in useJacoco) tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
      xml.required = true
      csv.required = false
      html.required = false
    }
  }

  @Suppress("UnstableApiUsage")
  testing.suites {
    val test by getting(JvmTestSuite::class) {
      useJUnitJupiter(rootProject.libs.versions.junit)
      targets.all {
        testTask.configure {
          if (name in useJacoco) finalizedBy(tasks.jacocoTestReport)
          jvmArgs = listOf("--enable-preview")
          enableAssertions = true
          reports.junitXml.mergeReruns = true
        }
      }
    }
  }

  tasks.withType<JavaExec>().configureEach { jvmArgs = listOf("--enable-preview"); enableAssertions = true }

  // Gradle module metadata contains Gradle JVM version, disable it
  tasks.withType<GenerateModuleMetadata>().configureEach {
    enabled = false
  }

  publishing.publications.create<MavenPublication>("maven") {
    val githubUrl = "https://github.com/aya-prover/aya-dev"
    groupId = proj.group.toString()
    version = proj.version.toString()
    artifactId = proj.name
    from(components["java"])
    pom {
      description = "The Aya proof assistant"
      name = proj.name
      url = "https://www.aya-prover.org"
      licenses {
        license { name = "MIT"; url = "$githubUrl/blob/master/LICENSE" }
      }
      developers {
        developer { id = "ice1000"; name = "Tesla (Yinsen) Zhang"; email = "ice1000kotlin@foxmail.com" }
        developer { id = "imkiva"; name = "Kiva Oyama"; email = "imkiva@islovely.icu" }
        developer { id = "re-xyr"; name = "Xy Ren"; email = "xy.r@outlook.com" }
        developer { id = "dark-flames"; name = "Darkflames"; email = "dark_flames@outlook.com" }
        developer { id = "tsao-chi"; name = "tsao-chi"; email = "tsao-chi@the-lingo.org" }
        developer { id = "lunalunaa"; name = "Luna Xin"; email = "luna.xin@outlook.com" }
        developer { id = "wsx"; name = "Shuxian Wang"; email = "wsx@berkeley.edu" }
        developer { id = "HoshinoTented"; name = "Hoshino Tented"; email = "limbolrain@gmail.com" }
      }
      scm {
        connection = "scm:git:$githubUrl"
        url = githubUrl
      }
    }
  }

  if (hasProperty("signing.keyId") && isRelease) signing {
    if (!hasProperty("signing.useBuiltinGpg")) useGpgCmd()
    sign(publishing.publications["maven"])
  }
}

val ossrhUsername = propOrEnv("mavenCentralPortalUsername")
val ossrhPassword = propOrEnv("mavenCentralPortalPassword")

if (ossrhUsername.isNotEmpty()) nmcpAggregation {
  centralPortal {
    username = ossrhUsername
    password = ossrhPassword
    if (isRelease) publishingType = "USER_MANAGED"
    else publishingType = "AUTOMATIC"
  }

  // Publish all projects that apply the 'maven-publish' plugin
  publishAllProjectsProbablyBreakingProjectIsolation()
}


apply { plugin("jacoco-report-aggregation") }
dependencies { useJacoco.forEach { jacocoAggregation(project(":$it")) { isTransitive = false } } }

val ccr = tasks.testCodeCoverageReport

if (Os.isFamily(Os.FAMILY_WINDOWS)) tasks.register("showCCR") {
  dependsOn(ccr)
  val path = "build\\reports\\jacoco\\testCodeCoverageReport\\html\\index.html"
  doLast { providers.exec { commandLine("cmd", "/c", "explorer", path) } }
}

tasks.withType<Sync>().configureEach {
  dependsOn(tasks.getByPath(":buildSrc:copyModuleInfo"))
}
