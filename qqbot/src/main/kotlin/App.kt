// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.

package org.aya.qqbot

import com.jcabi.manifests.Manifests
import net.mamoe.mirai.BotFactory
import net.mamoe.mirai.alsoLogin
import net.mamoe.mirai.event.subscribeMessages
import org.aya.api.error.CountingReporter
import org.aya.api.error.StreamReporter
import org.aya.cli.CompilerFlags
import org.aya.cli.SingleFileCompiler
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Paths

object AyaQQBot {
  private val env = Files.readAllLines(Paths.get("env"))
  private val bot = BotFactory.newBot(env[0].toLong(), env[1])
  init {
    Paths.get("tmp").let {
      if (!Files.exists(it))
        Files.createDirectory(it)
    }
    bot.eventChannel.subscribeMessages {
      startsWith("aya history ", true) quoteReply {
        it.toIntOrNull()?.let { id ->
          Files.readString(History.get(id))
        } ?: "Index is not a number"
      }
      case("aya info") quoteReply
        """Tweet~, I am here.
           Version: ${Manifests.read("Version")}.
           Build: ${Manifests.read("Build")}.""".trimIndent()
      startsWith("aya$", true) quoteReply { compile(it) }
    }
  }
  suspend fun join() {
    bot.alsoLogin().join()
  }
}

private fun compile(text: String): String {
  val hookOut = ByteArrayOutputStream()
  val file = History.add(text)
  val reporter = CountingReporter(StreamReporter(file, text, PrintStream(hookOut)))
  val e = SingleFileCompiler(reporter, file, null).compile(CompilerFlags.ASCII_FLAGS)
  return "$hookOut\n\nExit with $e"
}

suspend fun main() {
  AyaQQBot.join()
}
