// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.gradle


import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.tasks.Jar
import org.jetbrains.annotations.Nullable

import java.text.SimpleDateFormat

/**
 * @author ice1000, kiva
 */
final class CommonTasks {
  static TaskProvider<Jar> fatJar(Project project, @Nullable String mainClass) {
    project.tasks.register('fatJar', Jar) {
      it.archiveClassifier.set 'fat'
      it.from project.configurations.runtimeClasspath.collect {
        if (it.isDirectory()) it else project.zipTree(it)
      }
      it.duplicatesStrategy = DuplicatesStrategy.INCLUDE
      it.exclude '**/module-info.class'
      it.exclude '*.html'
      it.exclude 'META-INF/ECLIPSE_.*'
      def build = new SimpleDateFormat('yyyy/M/dd HH:mm:ss').format(new Date())
      if (mainClass != null) it.manifest.attributes(
        'Main-Class': mainClass,
        'Build': build
      ) else it.manifest.attributes('Build': build)
      def jar = project.tasks.jar
      it.dependsOn(jar)
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
