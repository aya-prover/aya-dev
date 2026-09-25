// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.

rootProject.name = "aya-prover"

@Suppress("UnstableApiUsage") dependencyResolutionManagement.repositories {
  mavenCentral()
}

include(
  "cli-impl",
  "cli-console",
  "tools",
  // Uses kala-primitives
  "jit-compiler",
  "tools-kala",
  "tools-md",
  "tools-repl",
  "syntax",
  "base",
  "pretty",
  "parser",
  "producer",
  "ide",
  "ide-lsp",
)

plugins {
  id("com.gradleup.nmcp.settings") version "1.6.2"
}

/** gradle.properties or environmental variables */
fun propOrEnv(name: String): String {
  val property = providers.gradleProperty(name)
  return if (property.isPresent) property.get()
  else System.getenv(name) ?: ""
}

val ossrhUsername = propOrEnv("mavenCentralPortalUsername")
val ossrhPassword = propOrEnv("mavenCentralPortalPassword")

if (ossrhUsername.isNotEmpty()) nmcpSettings {
  centralPortal {
    username = ossrhUsername
    password = ossrhPassword
    publishingType = if (System.getenv("CI").isEmpty()) "USER_MANAGED"
    else "AUTOMATIC"
  }
}
