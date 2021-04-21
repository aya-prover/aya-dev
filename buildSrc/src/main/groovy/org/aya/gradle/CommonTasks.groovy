// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.gradle

import org.gradle.api.Project
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.tasks.TaskProvider
import org.gradle.jvm.tasks.Jar

/**
 * @author ice1000
 */
class CommonTasks {
  static TaskProvider<Jar> fatJar(Project project) {
    return project.tasks.register("fatJar", Jar) {
      archiveClassifier.set("fat")
      from(project.configurations.runtimeClasspath.collect {
        if (it.isDirectory()) it else project.zipTree(it)
      })
      duplicatesStrategy = DuplicatesStrategy.INCLUDE
      exclude("**/module-info.class")
    }
  }
}
