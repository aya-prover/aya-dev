// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.

package org.aya.qqbot

import net.mamoe.mirai.BotFactory
import net.mamoe.mirai.alsoLogin
import net.mamoe.mirai.event.subscribeFriendMessages
import net.mamoe.mirai.event.subscribeGroupMessages
import org.aya.api.error.CountingReporter
import org.aya.api.error.StreamReporter
import org.aya.cli.CompilerFlags
import org.aya.cli.SingleFileCompiler
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*

object AyaQQBot {
  private val env = Files.readAllLines(Paths.get("env"))
  private val bot = BotFactory.newBot(env[0].toLong(), env[1])
  init {
    Paths.get("tmp").let {
      if (!Files.exists(it))
        Files.createDirectory(it)
    }
    bot.eventChannel.subscribeFriendMessages {
      startsWith("") reply { compile(it) }
    }
    bot.eventChannel.subscribeGroupMessages {
      startsWith("Aya$", true) reply { compile(it) }
    }
  }
  suspend fun join() {
    bot.alsoLogin().join()
  }
}

private fun compile(text: String): String {
  val file = Paths.get("tmp", UUID.randomUUID().toString())
  val hookOut = ByteArrayOutputStream()
  Files.write(file, text.toByteArray())
  val reporter = CountingReporter(StreamReporter(file, text, PrintStream(hookOut)))
  val e = SingleFileCompiler(reporter, file, null).compile(CompilerFlags.ASCII_FLAGS)
  Files.delete(file)
  return "$hookOut\n\nExit with $e"
}

suspend fun main() {
  AyaQQBot.join()
}
