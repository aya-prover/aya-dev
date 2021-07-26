// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.gradle

import com.ibm.icu.text.SimpleDateFormat
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.tasks.Jar

/**
 * @author ice1000
 */
final class CommonTasks {
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
      //noinspection GroovyAssignabilityCheck
      with project.tasks.jar
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
