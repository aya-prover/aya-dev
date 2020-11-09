// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
dependencies {
  api(project(":api"))
  implementation(project(":pretty"))
  implementation(project(":parser"))
  testImplementation(project(":tester"))
}

val genDir = file("src/main/gen")
val generateVersion = tasks.register<org.mzi.gradle.GenerateVersionTask>("generateVersion") {
  outputDir = genDir.resolve("org/mzi/prelude")
}

idea {
  module.generatedSourceDirs.add(genDir)
}

sourceSets.main {
  java.srcDirs(genDir)
}

tasks.compileJava {
  dependsOn(generateVersion)
}
