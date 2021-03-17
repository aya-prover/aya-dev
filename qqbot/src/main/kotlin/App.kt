// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.

package org.aya.qqbot

import com.jcabi.manifests.Manifests
import net.mamoe.mirai.BotFactory
import net.mamoe.mirai.alsoLogin
import net.mamoe.mirai.event.subscribeMessages
import net.mamoe.mirai.message.data.MessageChain
import net.mamoe.mirai.message.data.PlainText
import net.mamoe.mirai.message.data.QuoteReply
import net.mamoe.mirai.message.data.content
import org.aya.api.error.CountingReporter
import org.aya.api.error.StreamReporter
import org.aya.cli.CompilerFlags
import org.aya.cli.CompilerFlags.Message.ASCII
import org.aya.cli.SingleFileCompiler
import org.aya.prelude.GeneratedVersion
import org.glavo.kala.collection.immutable.ImmutableSeq
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets
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
      startsWith("aya history#") quoteReply {
        error {
          it.removePrefix("aya history#").trim().toInt().let { id ->
            Files.readString(History.get(id))
          }
        }
      }
      case("aya info") quoteReply
        """Tweet~, I am here.
          |Version: ${GeneratedVersion.VERSION_STRING}.
          |Build: ${Manifests.read("Build")}.""".trimMargin()
      startsWith("aya#") quoteReply {
        error {
          compile(it.removePrefix("aya#"))
        }
      }
      case("aya compile") quoteReply {
        message[QuoteReply]?.let {
          it.source.originalMessage.plainText().let { compile(it) }
        } ?: "Which message do you want to compile."
      }
    }
  }
  suspend fun join() {
    bot.alsoLogin().join()
  }
}

private fun MessageChain.plainText() =
  filterIsInstance<PlainText>().joinToString { it.content }

private inline fun error(f: () -> String): String {
  return try {
    f()
  } catch (e: Exception) {
    e.localizedMessage
  }
}

private fun compile(text: String): String {
  val hookOut = ByteArrayOutputStream()
  val file = History.add(text.toByteArray(StandardCharsets.UTF_8))
  val reporter = CountingReporter(StreamReporter(file, text, PrintStream(hookOut)))
  val e = SingleFileCompiler(reporter, file, null)
    .compile(CompilerFlags(ASCII, false, false, ImmutableSeq.of()))
  return "$hookOut\n\nExit with $e"
}

suspend fun main() {
  AyaQQBot.join()
}
