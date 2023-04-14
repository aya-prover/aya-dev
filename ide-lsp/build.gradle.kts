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
  const val theCurrent = "current"
  val supportedPlatforms = listOf(
    theCurrent,
    "windows-aarch64",
    "windows-x64",
    "linux-aarch64",
    "linux-x64",
    "macos-x64",
    "macos-aaarch64",
  )
}

fun jdkUrl(platform: String): String {
  val libericaJdkVersion = System.getProperty("java.vm.version")
  val fixAmd64 = platform.replace("x64", "amd64")
  val suffix = if (platform.contains("linux")) "tar.gz" else "zip"
  return "https://download.bell-sw.com/java/${libericaJdkVersion}/bellsoft-jdk${libericaJdkVersion}-${fixAmd64}.$suffix"
}

val allPlatformImageDir = buildDir.resolve("image-all-platforms")
jlink {
  addOptions("--strip-debug", "--compress", "2", "--no-header-files", "--no-man-pages")
  addExtraDependencies("jline-terminal-jansi")
  imageDir.set(allPlatformImageDir)
  mergedModule {
    uses("org.jline.terminal.impl.jansi.JansiTerminalProvider")
    requires("java.logging")
  }
  launcher {
    mainClass.set(Constants.mainClassQName)
    name = "aya-lsp"
    jvmArgs = mutableListOf("--enable-preview")
  }
  secondaryLauncher {
    name = "aya"
    jvmArgs = mutableListOf("--enable-preview")
    this as org.beryx.jlink.data.SecondaryLauncherData
    mainClass = "org.aya.cli.console.Main"
    moduleName = "aya.cli.console"
  }
  Constants.supportedPlatforms.forEach { platform ->
    targetPlatform(platform) {
      if (platform != Constants.theCurrent) setJdkHome(jdkDownload(jdkUrl(platform)))
    }
  }
}

val jlinkTask = tasks.named("jlink")
val ayaJlinkTask = tasks.register("jlinkAya")
val ayaImageDir = buildDir.resolve("image")
Constants.supportedPlatforms.forEach { platform ->
  val installDir = ayaImageDir.resolve(platform)
  val copyAyaExecutables = tasks.register<Copy>("copyAyaExecutables_$platform") {
    from(file("src/main/shell")) {
      // https://ss64.com/bash/chmod.html
      fileMode = "755".toInt(8)
      rename { it.removeSuffix(".sh") }
    }
    into(installDir.resolve("bin"))
  }

  val copyAyaJRE = tasks.register<Copy>("copyAyaJRE_$platform") {
    from(allPlatformImageDir.resolve("aya-lsp-$platform"))
    into(installDir.resolve(Constants.jreDirName))
    dependsOn(jlinkTask)
  }

  val copyAyaLibrary = tasks.register<Copy>("copyAyaLibrary_$platform") {
    from(rootProject.file("base/src/test/resources/success/common"))
    into(installDir.resolve("std"))
  }

  ayaJlinkTask.configure {
    dependsOn(copyAyaJRE)
    dependsOn(copyAyaExecutables)
    dependsOn(copyAyaLibrary)
  }
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
    dependsOn(ayaJlinkTask, prepareMergedJarsDirTask)
    from(ayaImageDir.resolve(Constants.theCurrent))
    into(destDir)
    doFirst { destDir.resolve(Constants.jreDirName).deleteRecursively() }
  }
}

tasks.withType<JavaCompile>().configureEach { CommonTasks.picocli(this) }
