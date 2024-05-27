// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
import org.aya.gradle.GenerateVersionTask

// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.

dependencies {
  api(project(":base"))
  implementation(libs.sourcebuddy)
  testImplementation(libs.junit.jupiter)
  testImplementation(libs.hamcrest)
  testImplementation(project(":producer"))
}

val genTestDir = file("src/test/gen")
idea.module.testSources.from(genTestDir)
idea.module.generatedSourceDirs.add(genTestDir)
sourceSets.test { java.srcDirs(genTestDir) }

val cleanGenerated = tasks.register("cleanGenerated") {
  group = "build"
  genTestDir.deleteRecursively()
}

tasks.named("clean") { dependsOn(cleanGenerated) }
tasks.withType<JavaExec>().configureEach {
  val vmArgs = jvmArgs ?: mutableListOf()
  vmArgs.add("-Xss32M")
  jvmArgs = vmArgs
}
