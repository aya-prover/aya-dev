// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
import org.apache.tools.ant.taskdefs.condition.Os

dependencies {
  val deps: java.util.Properties by rootProject.ext
  implementation("com.beust", "jcommander", version = deps.getProperty("version.jcommander"))
  implementation("org.ice1000.jimgui", "core", version = deps.getProperty("version.jimgui"))
  implementation(project(":base"))
  implementation(project(":parser"))
  implementation(project(":pretty"))
  testImplementation(project(":tester"))
}

plugins {
  id("org.beryx.jlink")
}

val mainClassQName = "org.aya.cli.Main"
tasks.withType<Jar>().configureEach {
  manifest.attributes["Main-Class"] = mainClassQName
}

tasks.withType<AbstractCopyTask>().configureEach {
  duplicatesStrategy = DuplicatesStrategy.INCLUDE
}

val isMac = Os.isFamily(Os.FAMILY_MAC)
if (isMac) tasks.withType<JavaExec>().configureEach {
  jvmArgs("-XstartOnFirstThread")
}

jlink {
  options.set(listOf("--strip-debug", "--compress", "2", "--no-header-files", "--no-man-pages"))
  mainClass.set(mainClassQName)
  launcher {
    name = "aya"
    jvmArgs = mutableListOf("--enable-preview")
    if (isMac) jvmArgs.add("-XstartOnFirstThread")
  }
}
