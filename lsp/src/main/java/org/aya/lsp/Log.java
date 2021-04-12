// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.lsp;

import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.services.LanguageClient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class Log {
  private static @Nullable LanguageClient CLIENT = null;

  public static void init(@NotNull LanguageClient client) {
    if (CLIENT == null) synchronized (Log.class) {
      if (CLIENT == null) CLIENT = client;
        // if the code was right, this should never happen
      else throw new IllegalStateException("double initialization occurred");
    }
  }

  public static void i(@NotNull String fmt, Object... args) {
    log(MessageType.Info, fmt, args);
  }

  public static void e(@NotNull String fmt, Object... args) {
    log(MessageType.Error, fmt, args);
  }

  public static void w(@NotNull String fmt, Object... args) {
    log(MessageType.Warning, fmt, args);
  }

  public static void d(@NotNull String fmt, Object... args) {
    log(MessageType.Log, fmt, args);
  }

  public static void log(@NotNull MessageType type, @NotNull String fmt, Object... args) {
    var format = fmt.formatted(args);
    logConsole(type, format);
    if (CLIENT != null) CLIENT.logMessage(new MessageParams(type, format));
  }

  public static void logConsole(@NotNull MessageType type, @NotNull String content) {
    System.err.printf("[%s]: %s%n", type, content);
  }
}
