plugins {
  java
  idea
  `java-library`
  `maven-publish`
}

var annotationsVersion: String by rootProject.ext
var protobufVersion: String by rootProject.ext
var antlrVersion: String by rootProject.ext

annotationsVersion = "20.1.0"
protobufVersion = "3.13.0"
antlrVersion = "4.8"

allprojects {
  group = "org.mzi"
  version = "0.1"
  repositories {
    jcenter()
    mavenCentral()
  }

  apply {
    plugin("java")
    plugin("idea")
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

  tasks.withType<Test> {
    jvmArgs = listOf("--enable-preview")
  }

  tasks.withType<JavaExec> {
    jvmArgs = listOf("--enable-preview")
  }
}

subprojects {
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
}

tasks.withType<Wrapper> {
  gradleVersion = "6.7"
}
