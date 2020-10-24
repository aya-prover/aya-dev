// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.

buildscript {
  repositories {
    maven(url = "https://plugins.gradle.org/m2/")
  }
  dependencies.classpath("org.javamodularity:moduleplugin:1.7.0")
}

plugins {
  java
  idea
  `java-library`
  `maven-publish`
}

var annotationsVersion: String by rootProject.ext
var protobufVersion: String by rootProject.ext
var antlrVersion: String by rootProject.ext
var kalaVersion: String by rootProject.ext

annotationsVersion = "20.1.0"
protobufVersion = "3.13.0"
antlrVersion = "4.8"
kalaVersion = "0.7.0"

allprojects {
  group = "org.mzi"
  version = "0.1"
}

val nonJavaProjects = listOf("docs")
subprojects {
  if (name in nonJavaProjects) return@subprojects

  apply {
    plugin("java")
    plugin("idea")
    plugin("org.javamodularity.moduleplugin")
    plugin("maven-publish")
    plugin("java-library")
  }

  repositories {
    jcenter()
    mavenCentral()
  }

  java {
    withSourcesJar()
    // Enable on-demand
    // withJavadocJar()
    sourceCompatibility = JavaVersion.VERSION_15
    targetCompatibility = JavaVersion.VERSION_15
  }

  idea {
    module {
      outputDir = file("out/production")
      testOutputDir = file("out/test")
    }
  }

  tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.isDeprecation = true
    options.release.set(15)
    options.compilerArgs.addAll(listOf("-Xlint:unchecked", "--enable-preview"))
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

  publishing {
    publications {
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
              // url.set("https://github.com/JetBrains/Arend/blob/master/LICENSE")
            }
          }
        }
      }
    }
  }

  val moduleName: String by this

  tasks.compileTestJava {
    extensions.configure(org.javamodularity.moduleplugin.extensions.ModuleOptions::class) {
      addModules = listOf("org.mzi.test")
      addReads = mapOf(moduleName to "org.mzi.test")
    }
  }

  tasks.test {
    extensions.configure(org.javamodularity.moduleplugin.extensions.TestModuleOptions::class) {
      runOnClasspath = true
    }
  }
}

tasks.withType<Wrapper> {
  gradleVersion = "6.7"
}
