// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.

import java.text.*
import java.util.*

plugins {
  java
  kotlin("jvm") version "1.4.31"
  id("com.github.johnrengelman.shadow")
}

tasks {
  compileKotlin {
    kotlinOptions.jvmTarget = "15"
  }
  compileTestKotlin {
    kotlinOptions.jvmTarget = "15"
  }
}

tasks.withType<Jar>().configureEach {
  manifest.attributes["Main-Class"] = "${project.group}.qqbot.AppKt"
}

val shadowJar: com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar by tasks
shadowJar.apply {
  manifest {
    attributes["Build"] = SimpleDateFormat("yyyy/M/dd HH:mm:ss").format(Date())
    attributes["Version"] = archiveVersion
  }
}

dependencies {
  val deps: java.util.Properties by rootProject.ext
  implementation(kotlin("stdlib"))
  implementation("net.mamoe:mirai-core:${deps.getProperty("version.mirai")}")
  implementation("com.jcabi:jcabi-manifests:0.7.5")
  implementation(project(":cli"))
  implementation(project(":api"))
  implementation(project(":base"))
}
