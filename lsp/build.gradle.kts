// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
import org.apache.tools.ant.taskdefs.condition.Os
import org.aya.gradle.CommonTasks

val mainClassQName = "org.aya.lsp.LspMain"
CommonTasks.fatJar(project, mainClassQName)

dependencies {
  val deps: java.util.Properties by rootProject.ext
  implementation(project(":cli"))
  implementation(project(":base"))
  implementation(project(":parser"))
  val lsp4jVersion = deps.getProperty("version.lsp4j")
  implementation("org.eclipse.lsp4j", "org.eclipse.lsp4j", version = lsp4jVersion)
  implementation("org.eclipse.lsp4j", "org.eclipse.lsp4j.jsonrpc", version = lsp4jVersion)
  implementation("com.beust", "jcommander", version = deps.getProperty("version.jcommander"))
}

plugins {
  id("org.beryx.jlink")
}

tasks.withType<JavaCompile>().configureEach {
  doFirst {
    options.compilerArgs.addAll(listOf("--module-path", classpath.asPath))
  }
}

val isMac = Os.isFamily(Os.FAMILY_MAC)

jlink {
  options.set(listOf("--strip-debug", "--compress", "2", "--no-header-files", "--no-man-pages"))
  launcher {
    mainClass.set(mainClassQName)
    name = "aya-lsp"
    jvmArgs = mutableListOf("--enable-preview")
  }
  secondaryLauncher {
    this as org.beryx.jlink.data.SecondaryLauncherData
    name = "aya"
    mainClass = "org.aya.cli.Main"
    moduleName = "org.aya.cli"
    jvmArgs = mutableListOf("--enable-preview")
  }
}

val jlinkTask = tasks.named("jlink")
val imageDir = buildDir.resolve("image")
jlinkTask.configure {
  doLast {
    file("aya.bat").copyTo(imageDir.resolve("bin/aya.bat"), overwrite = true)
    file("aya-lsp.bat").copyTo(imageDir.resolve("bin/aya-lsp.bat"), overwrite = true)

    val aya = imageDir.resolve("bin/aya")
    val ayalsp = imageDir.resolve("bin/aya-lsp")
    file("aya.sh").copyTo(aya, overwrite = true)
    file("aya-lsp.sh").copyTo(ayalsp, overwrite = true)
    aya.setExecutable(true)
    ayalsp.setExecutable(true)
  }
}

if (rootProject.hasProperty("installDir")) tasks.register<Copy>("install") {
  dependsOn(jlinkTask)
  from(imageDir)
  into(file(rootProject.property("installDir").toString()))
}
