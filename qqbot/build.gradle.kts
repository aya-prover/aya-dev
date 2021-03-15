// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.

plugins {
  java
  kotlin("jvm") version "1.4.31"
  id("com.github.johnrengelman.shadow")
}

tasks {
  compileKotlin {
    kotlinOptions.jvmTarget = "1.8"
  }
  compileTestKotlin {
    kotlinOptions.jvmTarget = "1.8"
  }
}

tasks.withType<Jar>().configureEach {
  manifest.attributes["Main-Class"] = "${project.group}.qqbot.AppKt"
}

dependencies {
  implementation(kotlin("stdlib"))
  implementation("net.mamoe:mirai-core:2.4.0")
  implementation(project(":cli"))
  implementation(project(":api"))
  implementation(project(":base"))
}
