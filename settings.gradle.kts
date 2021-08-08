// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
import java.util.*

rootProject.name = "aya-prover"

dependencyResolutionManagement {
  @Suppress("UnstableApiUsage") repositories {
    mavenCentral()
    maven(url = "https://jitpack.io")
  }
}

include(
  "api",
  "docs",
  "cli",
  "tgbot",
  // "proto",
  "base",
  "pretty",
  "lsp",
)

var deps = Properties()
deps.load(file("gradle/deps.properties").reader())
if (deps.getProperty("mirai") == "true") include("qqbot")
