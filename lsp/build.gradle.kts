// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
import org.aya.gradle.CommonTasks

val mainClassQName = "org.aya.lsp.LspMain"
CommonTasks.fatJar(project, mainClassQName)

dependencies {
  val deps: java.util.Properties by rootProject.ext
  api(project(":cli"))
  api("org.aya-prover.upstream", "javacs-protocol", version = deps.getProperty("version.aya-upstream"))
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

val ayaImageDir = buildDir.resolve("image")
val jlinkImageDir = ayaImageDir.resolve("jre")
jlink {
  addOptions("--strip-debug", "--compress", "2", "--no-header-files", "--no-man-pages")
  addExtraDependencies("jline-terminal-jansi")
  imageDir.set(jlinkImageDir)
  mergedModule {
    additive = true
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
@Suppress("unsupported")
jlinkTask.configure {
  inputs.files("aya.bat", "aya-lsp.bat", "aya.sh", "aya-lsp.sh")
  fun bin(name: String) = ayaImageDir.resolve("bin").resolve(name)
  fun jlinkBin(name: String) = jlinkImageDir.resolve("bin").resolve(name)
  doLast {
    ["aya", "aya-lsp"].forEach { name ->
      file("$name.sh").copyTo(bin(name), overwrite = true).setExecutable(true)
      file("$name.bat").copyTo(bin("$name.bat"), overwrite = true).setExecutable(true)
      jlinkBin(name).delete()
      jlinkBin("$name.bat").delete()
    }
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

if (rootProject.hasProperty("installDir")) {
  val destDir = file(rootProject.property("installDir")!!)
  // val dbi = tasks.register<Delete>("deleteBeforeInstall") {
  //   delete(File.listFiles(destDir))
  // }
  tasks.register<Copy>("install") {
    dependsOn(jlinkTask, prepareMergedJarsDirTask)
    from(ayaImageDir)
    into(destDir)
  }
}

tasks.withType<JavaCompile>().configureEach { CommonTasks.picocli(this) }
