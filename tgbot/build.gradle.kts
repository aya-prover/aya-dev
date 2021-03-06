// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
import org.aya.gradle.CommonTasks
CommonTasks.fatJar(project, "org.aya.tgbot.BotMain").configure {
  dependsOn(rootProject.tasks.getByPath(":cli:jar"))
}

dependencies {
  val deps: java.util.Properties by rootProject.ext
  implementation("com.github.pengrad", "java-telegram-bot-api", version = deps.getProperty("version.telegramapi"))
  implementation(project(":cli"))
  implementation(project(":api"))
  implementation(project(":base"))
}
