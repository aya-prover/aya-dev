// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
import org.aya.gradle.CommonTasks

CommonTasks.fatJar(project, Constants.mainClassQName)

dependencies {
  val deps: java.util.Properties by rootProject.ext
  // NOTE: use `api`. IntelliJ plugin needs it temporarily (should depend on ide instead of lsp).
  api(project(":ide"))
  api("org.aya-prover.upstream", "javacs-protocol", version = deps.getProperty("version.aya-upstream"))
  implementation(project(":cli-console"))
  implementation("info.picocli", "picocli", version = deps.getProperty("version.picocli"))
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

object Constants {
  const val jreDirName = "jre"
  const val mainClassQName = "org.aya.lsp.LspMain"
}

val ayaImageDir = buildDir.resolve("image")
val jlinkImageDir = ayaImageDir.resolve(Constants.jreDirName)
jlink {
  addOptions("--strip-debug", "--compress", "2", "--no-header-files", "--no-man-pages")
  addExtraDependencies("jline-terminal-jansi")
  imageDir.set(jlinkImageDir)
  mergedModule {
    additive = true
    uses("org.jline.terminal.spi.JansiSupport")
  }
  launcher {
    mainClass.set(Constants.mainClassQName)
    name = "aya-lsp"
    jvmArgs = mutableListOf("--enable-preview")
  }
  secondaryLauncher {
    this as org.beryx.jlink.data.SecondaryLauncherData
    name = "aya"
    mainClass = "org.aya.cli.console.Main"
    moduleName = "aya.cli.console"
    jvmArgs = mutableListOf("--enable-preview")
  }
}

val copyAyaExecutables = tasks.register<Copy>("copyAyaExecutables") {
  from(file("src/main/shell")) {
    // https://ss64.com/bash/chmod.html
    fileMode = "755".toInt(8)
    rename { it.removeSuffix(".sh") }
  }
  into(ayaImageDir.resolve("bin"))
}

val copyAyaLibrary = tasks.register<Copy>("copyAyaLibrary") {
  from(rootProject.file("base/src/test/resources/success/common"))
  into(ayaImageDir.resolve("std"))
}

val jlinkTask = tasks.named("jlink")
jlinkTask.configure {
  dependsOn(copyAyaExecutables)
  dependsOn(copyAyaLibrary)
}

val prepareMergedJarsDirTask = tasks.named("prepareMergedJarsDir")
prepareMergedJarsDirTask.configure {
  rootProject.subprojects
    .map { ":${it.name}:jar" }
    .mapNotNull(tasks::findByPath)
    .forEach {
      dependsOn(it)
      inputs.files(it.outputs.files)
    }
}

tasks.withType<AbstractCopyTask>().configureEach {
  duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

if (rootProject.hasProperty("installDir")) {
  val destDir = file(rootProject.property("installDir")!!)
  // val dbi = tasks.register<Delete>("deleteBeforeInstall") {
  //   delete(File.listFiles(destDir))
  // }
  tasks.register<Copy>("install") {
    dependsOn(jlinkTask, copyAyaExecutables, copyAyaLibrary, prepareMergedJarsDirTask)
    from(ayaImageDir)
    into(destDir)
    doFirst { destDir.resolve(Constants.jreDirName).deleteRecursively() }
  }
}

tasks.withType<JavaCompile>().configureEach { CommonTasks.picocli(this) }
