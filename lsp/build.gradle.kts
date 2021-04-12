// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.

dependencies {
  val deps: java.util.Properties by rootProject.ext
  implementation(project(":cli"))
  implementation(project(":api"))
  implementation(project(":base"))
}

plugins {
  id("com.github.johnrengelman.shadow")
}

tasks.withType<Jar>().configureEach {
  manifest.attributes["Main-Class"] = "org.aya.lsp.LspMain"
}

task<Copy>("copyJarHere") {
  dependsOn("shadowJar")
  from(buildDir.resolve("libs").resolve("${project.name}-$version-all.jar"))
  into(rootProject.buildDir)
}
