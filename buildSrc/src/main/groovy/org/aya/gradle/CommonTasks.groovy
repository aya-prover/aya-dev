// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.gradle

import org.graalvm.buildtools.gradle.dsl.GraalVMExtension
import org.graalvm.buildtools.gradle.tasks.BuildNativeImageTask
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.tasks.Jar
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.jvm.toolchain.JvmVendorSpec

import java.text.SimpleDateFormat

/**
 * @author ice1000, kiva
 */
final class CommonTasks {
  static void nativeImageBinaries(Project project, JavaToolchainService toolchain, GraalVMExtension ext, boolean allowNativeImage, boolean allowNativeTest) {
    ext.binaries.configureEach {
      fallback.set(false)
      verbose.set(true)
      sharedLibrary.set(false)
      buildArgs.add("-H:IncludeResources=messages/AnalysisBundle.properties") // ij-parsing-core
      buildArgs.add("--report-unsupported-elements-at-runtime")

      var javaVersion = project.rootProject.property("javaVersion") as int
      javaLauncher.set(
        toolchain.launcherFor {
          languageVersion.set(JavaLanguageVersion.of(javaVersion))
          vendor.set(JvmVendorSpec.GRAAL_VM)
        },
      )
    }

    if (!allowNativeImage) project.tasks.named("nativeCompile", BuildNativeImageTask) {
      it.enabled = false
    }
    if (!allowNativeTest) ext.testSupport.set(false)
  }

  static TaskProvider<GenerateReflectionConfigTask> nativeImageConfig(Project project) {
    var root = project.projectDir.toPath()
    var configGenDir = root.resolve("build/native-config").toFile()
    var configTemplateFile = root.resolve("reflect-config.txt").toFile()
    var metaInfDir = root.resolve(
      "src/main/resources/META-INF/native-image/${project.group}.${project.name}"
    ).toFile()

    var task = project.tasks.register('generateNativeImageConfig', GenerateReflectionConfigTask) {
      outputDir = configGenDir
      inputFile = configTemplateFile
      outputs.upToDateWhen { false }
      doFirst {
        metaInfDir.mkdirs()
      }
      doLast {
        project.copy {
          from(configGenDir)
          into(metaInfDir)
        }
      }
    }
    var cleanMetaInf = project.tasks.register("cleanNativeImageConfig") {
      metaInfDir.deleteDir()
    }
    project.tasks.named("compileJava") { dependsOn(task) }
    project.tasks.named("sourcesJar") { dependsOn(task) }
    project.tasks.named("clean") { dependsOn(cleanMetaInf) }
    task
  }

  static TaskProvider<Jar> fatJar(Project project, String mainClass) {
    project.tasks.register('fatJar', Jar) {
      archiveClassifier.set 'fat'
      from project.configurations.runtimeClasspath.collect {
        if (it.isDirectory()) it else project.zipTree(it)
      }
      duplicatesStrategy = DuplicatesStrategy.INCLUDE
      exclude '**/module-info.class'
      exclude '*.html'
      exclude 'META-INF/ECLIPSE_.*'
      manifest.attributes(
        'Main-Class': mainClass,
        'Build': new SimpleDateFormat('yyyy/M/dd HH:mm:ss').format(new Date())
      )
      def jar = project.tasks.jar
      dependsOn(jar)
      //noinspection GroovyAssignabilityCheck
      with jar
    }
  }

  static def avoidModuleInfo(Task task) {
    def projectDir = task.project.projectDir
    def moduleInfo = projectDir.resolve("src/main/java/module-info.java")
    def moduleCache = projectDir.resolve("src/main/module-info.java")
    task.doFirst {
      moduleInfo.copyTo(moduleCache)
      moduleInfo.delete()
    }
    task.doLast {
      moduleCache.copyTo(moduleInfo)
      moduleCache.delete()
    }
  }

  static def picocli(JavaCompile task) {
    var project = task.project
    // https://picocli.info/#_enabling_the_annotation_processor
    task.options.compilerArgs.add("-Aproject=${project.group}/${project.name}")
  }
}
