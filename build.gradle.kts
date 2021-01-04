// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
import java.util.Properties

plugins {
  java
  jacoco
  idea
  `java-library`
  `maven-publish`
  id("io.freefair.lombok") version "5.3.0"
}

var deps: Properties by rootProject.ext

deps = Properties()
deps.load(file("gradle/deps.properties").reader())

allprojects {
  group = "org.mzi"
  version = deps.getProperty("version.project")
}

val nonJavaProjects = listOf("docs")
@Suppress("UnstableApiUsage")
subprojects {
  if (name in nonJavaProjects) return@subprojects

  apply {
    plugin("java")
    plugin("idea")
    plugin("jacoco")
    plugin("maven-publish")
    plugin("java-library")
    plugin("io.freefair.lombok")
  }

  repositories {
    jcenter()
    mavenCentral()
    maven(url = "https://dl.bintray.com/glavo/maven")
  }

  java {
    withSourcesJar()
    // Enable on-demand
    // withJavadocJar()
    sourceCompatibility = JavaVersion.VERSION_15
    targetCompatibility = JavaVersion.VERSION_15
  }

  jacoco {
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

  tasks.jacocoTestReport {
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
  }

  tasks.withType<JavaExec>().configureEach {
    jvmArgs = listOf("--enable-preview")
    enableAssertions = true
  }

  publishing.publications {
    create<MavenPublication>("maven") {
      groupId = this@subprojects.group.toString()
      version = this@subprojects.version.toString()
      artifactId = this@subprojects.name
      from(components["java"])
      pom {
        // url.set("https://arend-lang.github.io")
        licenses {
          license {
            name.set("Apache-2.0")
            url.set("https://github.com/ice1000/mzi/blob/master/LICENSE")
          }
        }
      }
    }
  }
}

tasks.withType<Wrapper>().configureEach {
  gradleVersion = "6.7"
}
