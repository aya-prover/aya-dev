// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
import org.apache.tools.ant.taskdefs.condition.Os
import java.util.*

plugins {
  java
  jacoco
  idea
  `java-library`
  `maven-publish`
  signing
  id("org.beryx.jlink") version "2.24.1" apply false
}

var deps: Properties by rootProject.ext

deps = Properties()
file("gradle/deps.properties").reader().use(deps::load)

allprojects {
  group = "org.aya-prover"
  version = deps.getProperty("version.project")
}

subprojects {
  val useJacoco = name in listOf("base", "pretty")

  apply {
    plugin("java")
    plugin("idea")
    if (useJacoco) plugin("jacoco")
    plugin("maven-publish")
    plugin("java-library")
    plugin("signing")
  }

  java {
    withSourcesJar()
    if (hasProperty("release")) withJavadocJar()
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
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

    options.apply {
      encoding = "UTF-8"
      isDeprecation = true
      release.set(17)
      compilerArgs.addAll(listOf("-Xlint:unchecked", "--enable-preview"))
    }
  }

  tasks.withType<Javadoc>().configureEach {
    val options = options as StandardJavadocDocletOptions
    options.addBooleanOption("-enable-preview", true)
    options.addStringOption("-source", "17")
    options.encoding("UTF-8")
    options.tags(
      "apiNote:a:API Note:",
      "implSpec:a:Implementation Requirements:",
      "implNote:a:Implementation Note:",
    )
  }

  artifacts {
    add("archives", tasks["sourcesJar"])
    if (hasProperty("release")) add("archives", tasks["javadocJar"])
  }

  if (useJacoco) tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports { configureReports(false) }
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

  if (hasProperty("ossrhUsername")) publishing.repositories {
    maven("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2") {
      name = "MavenCentral"
      credentials {
        username = property("ossrhUsername").toString()
        password = property("ossrhPassword").toString()
      }
    }
  }

  val proj = this@subprojects
  publishing.publications {
    create<MavenPublication>("maven") {
      val githubUrl = "https://github.com/aya-prover/aya-dev"
      groupId = proj.group.toString()
      version = proj.version.toString()
      artifactId = proj.name
      from(components["java"])
      pom {
        description.set("The Aya proof assistant")
        name.set(proj.name)
        url.set("https://www.aya-prover.org")
        licenses {
          license {
            name.set("GPL-3.0")
            url.set("$githubUrl/blob/master/LICENSE")
          }
        }
        developers {
          developer {
            id.set("ice1000")
            name.set("Tesla Ice Zhang")
            email.set("ice1000kotlin@foxmail.com")
          }
          developer {
            id.set("imkiva")
            name.set("Kiva Oyama")
            email.set("imkiva@islovely.icu")
          }
          developer {
            id.set("re-xyr")
            name.set("Xy Ren")
            email.set("xy.r@outlook.com")
          }
        }
        scm {
          connection.set("scm:git:$githubUrl")
          url.set(githubUrl)
        }
      }
    }
  }

  if (hasProperty("signing.keyId")) signing {
    sign(publishing.publications["maven"])
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

  reports { configureReports(true) }
  doLast {
    if (Os.isFamily(Os.FAMILY_WINDOWS) && System.getenv("CI") != "true") exec {
      commandLine("explorer.exe", ".\\build\\reports\\jacoco\\mergeJacocoReports\\html\\index.html")
    }
  }
}

tasks.register("githubActions") {
  group = "verification"
  dependsOn(mergeJacocoReports, tasks.findByPath(":lsp:jlink"))
}

tasks.withType<Sync>().configureEach {
  dependsOn(tasks.findByPath(":buildSrc:copyModuleInfo"))
}

fun JacocoReportsContainer.configureReports(merger: Boolean) {
  xml.required.set(true)
  csv.required.set(false)
  html.required.set(merger)
}
