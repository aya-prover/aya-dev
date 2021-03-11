// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tgbot;

import com.pengrad.telegrambot.TelegramBot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * @author ice1000
 */
public class BotMain {
  public static void main(String... args) throws IOException {
    var properties = new Properties();
    properties.load(Files.newInputStream(Paths.get("gradle.properties")));
    var botToken = properties.getProperty("aya.telegram.token");
    System.out.println(botToken);
    var bot = new TelegramBot(botToken);
    bot.setUpdatesListener(new AyaBot(bot));
  }
}
