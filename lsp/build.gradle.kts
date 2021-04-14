// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
import org.apache.tools.ant.taskdefs.condition.Os

dependencies {
  val deps: java.util.Properties by rootProject.ext
  implementation(project(":cli"))
  implementation(project(":api"))
  implementation(project(":base"))
  implementation(project(":parser"))
  implementation("org.eclipse.lsp4j", "org.eclipse.lsp4j", version = deps.getProperty("version.lsp4j"))
  implementation("org.eclipse.lsp4j", "org.eclipse.lsp4j.jsonrpc", version = deps.getProperty("version.lsp4j"))
  implementation("com.beust", "jcommander", version = deps.getProperty("version.jcommander"))
}

plugins {
  id("org.beryx.jlink")
}

val lspMainClassQName = "org.aya.lsp.LspMain"

tasks.withType<Jar>().configureEach {
  manifest.attributes["Main-Class"] = lspMainClassQName
}

tasks.withType<JavaCompile> {
  doFirst {
    options.compilerArgs.addAll(listOf("--module-path", classpath.asPath))
  }
}

val isMac = Os.isFamily(Os.FAMILY_MAC)

jlink {
  options.set(listOf("--strip-debug", "--compress", "2", "--no-header-files", "--no-man-pages"))
  launcher {
    mainClass.set(lspMainClassQName)
    name = "aya-lsp"
    jvmArgs = mutableListOf("--enable-preview")
  }
  secondaryLauncher {
    this as org.beryx.jlink.data.SecondaryLauncherData
    name = "aya"
    mainClass = "org.aya.cli.Main"
    moduleName = "org.aya.cli"
    jvmArgs = mutableListOf("--enable-preview")
    if (isMac) jvmArgs.add("-XstartOnFirstThread")
  }
}

val jlinkTask = tasks.named("jlink")
jlinkTask.configure {
  doLast {
    file("aya.bat").copyTo(buildDir.resolve("image/bin/aya.bat"), overwrite = true)
    file("aya-lsp.bat").copyTo(buildDir.resolve("image/bin/aya-lsp.bat"), overwrite = true)
  }
}

if (rootProject.hasProperty("installDir")) tasks.register<Copy>("install") {
  dependsOn(jlinkTask)
  from(buildDir.resolve("image"))
  into(file(rootProject.property("installDir").toString()))
}
