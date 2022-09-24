// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
import org.aya.gradle.CommonTasks

val mainClassQName = "org.aya.lsp.LspMain"
CommonTasks.fatJar(project, mainClassQName)

dependencies {
  val deps: java.util.Properties by rootProject.ext
  implementation(project(":cli"))
  implementation("org.aya-prover.upstream", "javacs-protocol", version = deps.getProperty("version.aya-upstream"))
  val lsp4jVersion = deps.getProperty("version.lsp4j")
  implementation("org.eclipse.lsp4j", "org.eclipse.lsp4j", version = lsp4jVersion)
  implementation("org.eclipse.lsp4j", "org.eclipse.lsp4j.jsonrpc", version = lsp4jVersion)
  annotationProcessor("info.picocli", "picocli-codegen", version = deps.getProperty("version.picocli"))
  testImplementation("org.junit.jupiter", "junit-jupiter", version = deps.getProperty("version.junit"))
  testImplementation("org.hamcrest", "hamcrest", version = deps.getProperty("version.hamcrest"))
}

plugins {
  id("org.beryx.jlink")
}

tasks.withType<JavaCompile>().configureEach {
  doFirst {
    options.compilerArgs.addAll(listOf("--module-path", classpath.asPath))
  }
}

tasks.named<Test>("test") {
  testLogging.showStandardStreams = true
  testLogging.showCauses = true
  inputs.dir(projectDir.resolve("src/test/resources"))
}

jlink {
  addOptions("--strip-debug", "--compress", "2", "--no-header-files", "--no-man-pages")
  addExtraDependencies("jline-terminal-jansi")
  mergedModule {
    additive = true
    requires("com.google.gson")
    uses("org.jline.terminal.spi.JansiSupport")
  }
  launcher {
    mainClass.set(mainClassQName)
    name = "aya-lsp"
    jvmArgs = mutableListOf("--enable-preview")
  }
  secondaryLauncher {
    this as org.beryx.jlink.data.SecondaryLauncherData
    name = "aya"
    mainClass = "org.aya.cli.Main"
    moduleName = "aya.cli"
    jvmArgs = mutableListOf("--enable-preview")
  }
}

val jlinkTask = tasks.named("jlink")
val imageDir = buildDir.resolve("image")
jlinkTask.configure {
  doFirst {
    file("aya.bat").copyTo(imageDir.resolve("bin/aya.bat"), overwrite = true)
    file("aya-lsp.bat").copyTo(imageDir.resolve("bin/aya-lsp.bat"), overwrite = true)

    file("aya.sh").copyTo(imageDir.resolve("bin/aya"), overwrite = true).setExecutable(true)
    file("aya-lsp.sh").copyTo(imageDir.resolve("bin/aya-lsp"), overwrite = true).setExecutable(true)
  }
}
val prepareMergedJarsDirTask = tasks.named("prepareMergedJarsDir")
prepareMergedJarsDirTask.configure {
  val libs = listOf("cli", "base", "pretty", "tools", "tools-repl", "parser")
  libs.map { ":$it:jar" }.mapNotNull(tasks::findByPath).forEach {
    dependsOn(it)
    inputs.files(it.outputs.files)
  }
}

tasks.withType<AbstractCopyTask>().configureEach {
  duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

if (rootProject.hasProperty("installDir")) tasks.register<Copy>("install") {
  dependsOn(jlinkTask, prepareMergedJarsDirTask)
  from(imageDir)
  into(file(rootProject.property("installDir")!!))
}

tasks.withType<JavaCompile>().configureEach { CommonTasks.picocli(this) }
