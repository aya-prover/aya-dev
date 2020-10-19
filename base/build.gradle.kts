dependencies {
  val annotationsVersion: String by rootProject.ext
  api("org.jetbrains:annotations:$annotationsVersion")
  api(project(":api"))
  testImplementation(project(":tester"))
}

val genDir = file("src/main/gen")
val generateVersion = tasks.register<org.mzi.gradle.GenerateVersionTask>("generateVersion") {
  outputDir = genDir.resolve("org/mzi/prelude")
}

idea {
  module.generatedSourceDirs.add(genDir)
}

sourceSets {
  main {
    java.srcDirs(genDir)
  }
}

tasks.compileJava {
  dependsOn(generateVersion)
}
