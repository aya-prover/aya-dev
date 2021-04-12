// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tgbot;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.SendMessage;
import org.aya.api.error.StreamReporter;
import org.aya.cli.CompilerFlags;
import org.aya.cli.SingleFileCompiler;
import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

/**
 * @author ice1000
 */
public record AyaBot(@NotNull TelegramBot bot) implements UpdatesListener {
  public static final Charset CHARSET = StandardCharsets.UTF_8;

  @Override public int process(List<Update> updates) {
    updates.forEach(this::onUpdateReceived);
    return CONFIRMED_UPDATES_ALL;
  }

  public void onUpdateReceived(Update update) {
    var m = update.message();
    var em = update.editedMessage();
    if (m != null) replyTo(m.text(), m.chat().id());
    else if (em != null) replyTo(em.text(), em.chat().id());
  }

  private void replyTo(String txt, long chatId) {
    if (txt == null) return;
    if (txt.startsWith("\\")) bot.execute(new SendMessage(chatId, computeMessage(txt)));
  }

  private String computeMessage(String txt) {
    var file = Paths.get("build", "telegramCache");
    try {
      Files.writeString(file, txt, CHARSET);
      var hookOut = new ByteArrayOutputStream();
      var reporter = new StreamReporter(file, txt, new PrintStream(hookOut));
      var e = new SingleFileCompiler(reporter, null)
        .compile(file, new CompilerFlags(CompilerFlags.Message.ASCII, false, null, ImmutableSeq.of()));
      return hookOut.toString(CHARSET) + "\n\n Exited with " + e;
    } catch (IOException e) {
      return "error reading file " + file.toAbsolutePath();
    }
  }
}
