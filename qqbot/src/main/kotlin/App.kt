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
import java.util.*

object AyaQQBot {
  private val env = File("env").readLines()
  private val bot = BotFactory.newBot(env[0].toLong(), env[1])
  init {
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
  val file = File.createTempFile("aya-qq-bot", "")
  val hookOut = ByteArrayOutputStream()
  file.writeText(text)
  val reporter = CountingReporter(StreamReporter(file.toPath(), text, PrintStream(hookOut)))
  val e = SingleFileCompiler(reporter, file.toPath(), null).compile(CompilerFlags.ASCII_FLAGS)
  file.delete()
  return "$hookOut\n\nExit with $e"
}

suspend fun main() {
  AyaQQBot.join()
}
