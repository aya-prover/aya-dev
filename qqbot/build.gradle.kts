// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.

import org.aya.gradle.CommonTasks
import java.util.*

CommonTasks.fatJar(project, "org.aya.qqbot.AppKt")

plugins {
  java
  kotlin("jvm") version "1.4.31"
}

tasks {
  compileKotlin {
    kotlinOptions.jvmTarget = "15"
  }
  compileTestKotlin {
    kotlinOptions.jvmTarget = "15"
  }
}

dependencies {
  val deps: Properties by rootProject.ext
  implementation(kotlin("stdlib"))
  implementation("net.mamoe:mirai-core:${deps.getProperty("version.mirai")}")
  implementation("com.jcabi:jcabi-manifests:${deps.getProperty("version.manifests")}")
  implementation(project(":cli"))
  implementation(project(":base"))
}
