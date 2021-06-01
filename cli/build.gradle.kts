// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
import org.apache.tools.ant.taskdefs.condition.Os
import org.aya.gradle.CommonTasks
CommonTasks.fatJar(project, "org.aya.cli.Main")

dependencies {
  val deps: java.util.Properties by rootProject.ext
  implementation("info.picocli", "picocli", version = deps.getProperty("version.picocli"))
  annotationProcessor("info.picocli", "picocli-codegen", version = deps.getProperty("version.picocli"))
  implementation("com.google.code.gson", "gson", version = deps.getProperty("version.gson"))
  implementation("org.ice1000.jimgui", "core", version = deps.getProperty("version.jimgui"))
  implementation(project(":base"))
  implementation(project(":parser"))
  implementation(project(":pretty"))
  testImplementation("org.junit.jupiter", "junit-jupiter", version = deps.getProperty("version.junit"))
  testImplementation("org.hamcrest", "hamcrest", version = deps.getProperty("version.hamcrest"))
}

tasks.withType<AbstractCopyTask>().configureEach {
  duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

val isMac = Os.isFamily(Os.FAMILY_MAC)
if (isMac) tasks.withType<JavaExec>().configureEach {
  jvmArgs("-XstartOnFirstThread")
}

tasks.withType<JavaCompile> {
  options.compilerArgs.add("-Aproject=${project.group}/${project.name}")
}
