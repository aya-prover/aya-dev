// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tgbot;

import org.aya.api.error.CountingReporter;
import org.aya.api.error.StreamReporter;
import org.aya.cli.CompilerFlags;
import org.aya.cli.SingleFileCompiler;
import org.jetbrains.annotations.NotNull;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * @author ice1000
 */
public class AyaBot extends TelegramLongPollingBot {
  private final @NotNull
  Properties properties = new Properties();

  public AyaBot() throws IOException {
    properties.load(Files.newInputStream(Paths.get("gradle.properties")));
  }

  @Override public String getBotUsername() {
    return "Aya REPL Bot";
  }

  @Override public String getBotToken() {
    return properties.getProperty("aya.telegram.token");
  }

  @Override public void onUpdateReceived(Update update) {
    if (update.hasMessage()) {
      var m = update.getMessage();
      if (m.hasText()) replyTo(m.getText(), m.getChatId());
    } else if (update.hasEditedMessage()) {
      var m = update.getEditedMessage();
      if (m.hasText()) replyTo(m.getText(), m.getChatId());
    }
  }

  private void replyTo(String txt, long chatId) {
    if (txt.startsWith("\\")) try {
      execute(sendMessage(txt, chatId));
    } catch (TelegramApiException e) {
      e.printStackTrace();
    }
  }

  @NotNull private SendMessage sendMessage(String txt, long chatId) {
    var file = Paths.get("build", "telegramCache");
    var send = new SendMessage();
    send.setChatId(String.valueOf(chatId));
    try {
      Files.writeString(file, txt, StandardCharsets.UTF_8);
      var hookOut = new ByteArrayOutputStream();
      var reporter = new CountingReporter(new StreamReporter(
        file, txt, new PrintStream(hookOut)));
      var e = new SingleFileCompiler(reporter, file, null)
        .compile(CompilerFlags.ASCII_FLAGS);
      send.setText(hookOut + "\n\n Exited with " + e);
    } catch (IOException e) {
      send.setText("error reading file " + file.toAbsolutePath());
    }
    return send;
  }
}
