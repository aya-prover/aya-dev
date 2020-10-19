dependencies {
  val antlrVersion: String by rootProject.ext
  api("org.antlr:antlr4-runtime:$antlrVersion")
}

idea {
  module.generatedSourceDirs.add(file("src/main/java"))
}
