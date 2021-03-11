// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tgbot;

import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.io.IOException;

/**
 * @author ice1000
 */
public class BotMain {
  public static void main(String... args) throws TelegramApiException, IOException {
    var bots = new TelegramBotsApi(DefaultBotSession.class);
    bots.registerBot(new AyaBot());
  }
}
