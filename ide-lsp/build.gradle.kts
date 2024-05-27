// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
import org.aya.gradle.CommonTasks
import org.aya.gradle.JdkUrls
import java.nio.file.Files
import java.security.MessageDigest
import java.util.*

CommonTasks.fatJar(project, Constants.mainClassQName)

dependencies {
  // NOTE: use `api`. IntelliJ plugin needs it temporarily (should depend on ide instead of lsp).
  api(project(":ide"))
  api(libs.aya.lsp.protocol)
  implementation(project(":cli-console"))
  implementation(libs.picocli.runtime)
  annotationProcessor(libs.picocli.codegen)
  testImplementation(libs.junit.jupiter)
  testImplementation(libs.hamcrest)
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

val supportedPlatforms: List<String> by rootProject.ext
val currentPlatform: String by rootProject.ext
val javaVersion: Int by rootProject.ext

fun jdkUrl(platform: String): String = JdkUrls(javaVersion, platform).jdk()

val allPlatformImageDir = layout.buildDirectory.asFile.get().resolve("image-all-platforms")
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
  supportedPlatforms.forEach { platform ->
    targetPlatform(platform) {
      if (platform != currentPlatform) setJdkHome(jdkDownload(jdkUrl(platform)))
    }
  }
}

val jlinkTask = tasks.named("jlink")
val ayaJlinkTask = tasks.register("jlinkAya")
val ayaJlinkZipTask = tasks.register("jlinkAyaZip")
val ayaImageDir = layout.buildDirectory.asFile.get().resolve("image")
supportedPlatforms.forEach { platform ->
  val installDir = ayaImageDir.resolve(platform)
  val copyAyaExecutables = tasks.register<Copy>("copyAyaExecutables_$platform") {
    from(file("src/main/shell")) {
      // https://ss64.com/bash/chmod.html
      filePermissions { unix("755") }
      if (platform.contains("windows")) {
        include("*.bat")
      } else {
        include("*.sh")
        rename { it.removeSuffix(".sh") }
      }
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

  val copyAyaLicense = tasks.register<Copy>("copyAyaLicense_$platform") {
    from(rootProject.file("LICENSE"))
    into(installDir.resolve("licenses"))
  }

  val packageAya = tasks.register<Zip>("packageAya_$platform") {
    val fileName = "aya-prover_jlink_$platform.zip"
    val sha256FileName = "$fileName.sha256.txt"
    dependsOn(copyAyaJRE)
    dependsOn(copyAyaExecutables)
    dependsOn(copyAyaLibrary)
    archiveFileName.set(fileName)
    destinationDirectory.set(ayaImageDir)
    from(installDir) {
      exclude("bin/aya")
      exclude("bin/aya-lsp")
      exclude("${Constants.jreDirName}/bin/**")
    }
    from(installDir) {
      include("bin/aya")
      include("bin/aya-lsp")
      include("${Constants.jreDirName}/bin/**")
      fileMode = "755".toInt(8)
    }
    doLast {
      val bytes = MessageDigest.getInstance("SHA-256")
        .digest(archiveFile.get().asFile.readBytes())
      val sha256 = HexFormat.of().withLowerCase().formatHex(bytes)
      Files.writeString(
        destinationDirectory.get().file(sha256FileName).asFile.toPath(),
        sha256,
      )
    }
  }

  ayaJlinkTask.configure {
    dependsOn(copyAyaJRE, copyAyaExecutables, copyAyaLibrary, copyAyaLicense)
  }
  ayaJlinkZipTask.configure {
    dependsOn(packageAya)
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
    from(ayaImageDir.resolve(currentPlatform))
    into(destDir)
    doFirst { destDir.resolve(Constants.jreDirName).deleteRecursively() }
  }
}

tasks.withType<JavaCompile>().configureEach { CommonTasks.picocli(this) }
