plugins {
  java
  groovy
  antlr
}

repositories { jcenter() }

tasks.withType<AntlrTask>().configureEach {
  outputDirectory = projectDir.parentFile.resolve("parser/src/main/java")
  arguments.addAll(listOf(
    "-package", "org.mzi.parser",
    "-no-listener",
    "-visitor"
  ))
}

dependencies {
  antlr("org.antlr:antlr4:4.8")
}
