// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
import java.util.*

plugins {
  java
  jacoco
  idea
  `java-library`
  `maven-publish`
}

var deps: Properties by rootProject.ext

deps = Properties()
deps.load(file("gradle/deps.properties").reader())

allprojects {
  group = "org.aya"
  version = deps.getProperty("version.project")
}

@Suppress("UnstableApiUsage")
subprojects {
  if (name in listOf("docs")) return@subprojects
  val useJacoco = name in listOf("base", "tester")

  apply {
    plugin("java")
    plugin("idea")
    if (useJacoco) plugin("jacoco")
    plugin("maven-publish")
    plugin("java-library")
  }

  java {
    withSourcesJar()
    // Enable on-demand
    // withJavadocJar()
    sourceCompatibility = JavaVersion.VERSION_15
    targetCompatibility = JavaVersion.VERSION_15
  }

  if (useJacoco) jacoco {
    toolVersion = deps.getProperty("version.jacoco")
  }

  idea.module {
    outputDir = file("out/production")
    testOutputDir = file("out/test")
  }

  tasks.withType<JavaCompile>().configureEach {
    modularity.inferModulePath.set(true)

    options.encoding = "UTF-8"
    options.isDeprecation = true
    options.release.set(15)
    options.compilerArgs.addAll(listOf("-Xlint:unchecked", "--enable-preview"))
  }

  if (useJacoco) tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
      xml.isEnabled = false
      csv.isEnabled = false
      html.isEnabled = true
      html.destination = buildDir.resolve("jacocoHtml")
    }
  }

  tasks.withType<Test>().configureEach {
    jvmArgs = listOf("--enable-preview")
    useJUnitPlatform()
    enableAssertions = true
    reports.junitXml.mergeReruns.set(true)
  }

  tasks.withType<JavaExec>().configureEach {
    jvmArgs = listOf("--enable-preview")
    enableAssertions = true
  }

  val proj = this@subprojects
  publishing.publications {
    create<MavenPublication>("maven") {
      groupId = proj.group.toString()
      version = proj.version.toString()
      artifactId = proj.name
      from(components["java"])
      pom {
        // url.set("https://arend-lang.github.io")
        licenses {
          license {
            name.set("GPL-3.0")
            url.set("https://github.com/ice1000/aya-prover/blob/master/LICENSE")
          }
        }
      }
    }
  }
}

val mergeJacocoReports = tasks.register<JacocoReport>("mergeJacocoReports") {
  group = "verification"
  subprojects.forEach { subproject ->
    subproject.plugins.withType<JacocoPlugin>().configureEach {
      subproject.tasks.matching { it.extensions.findByType<JacocoTaskExtension>() != null }.configureEach {
        sourceSets(subproject.sourceSets.main.get())
        executionData(this)
      }

      subproject.tasks.matching { it.extensions.findByType<JacocoTaskExtension>() != null }.forEach {
        dependsOn(it)
      }
    }
  }

  reports {
    xml.isEnabled = false
    csv.isEnabled = false
    html.isEnabled = true
  }
}

tasks.register("githubActions") {
  group = "verification"
  dependsOn(tasks.named("check"), mergeJacocoReports, tasks.findByPath(":cli:copyJarHere"))
}

tasks.withType<Wrapper>().configureEach {
  gradleVersion = "6.8.1"
}
