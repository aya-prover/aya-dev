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
kalaVersion = "0.6.2"

val nonJavaProjects = listOf("docs")
allprojects {
  group = "org.mzi"
  version = "0.1"

  if (name in nonJavaProjects) return@allprojects

  repositories {
    jcenter()
    mavenCentral()
  }

  apply {
    plugin("java")
    plugin("idea")
    plugin("org.javamodularity.moduleplugin")
  }

  java {
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
}

subprojects {
  if (name in nonJavaProjects) return@subprojects

  apply {
    plugin("maven-publish")
    plugin("java-library")
  }

  java {
    withSourcesJar()
    // Enable on-demand
    // withJavadocJar()
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
