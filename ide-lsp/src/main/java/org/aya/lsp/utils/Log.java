// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.lsp.utils;

import org.aya.lsp.server.AyaLanguageClient;
import org.aya.util.Panic;
import org.intellij.lang.annotations.PrintFormat;
import org.javacs.lsp.ShowMessageParams;
import org.javacs.lsp.ShowMessageType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

public class Log {
  private static final @NotNull Path LOG_FILE = Paths.get("aya-last-startup.log").toAbsolutePath();
  private static volatile @Nullable AyaLanguageClient CLIENT = null;

  public static void init(@NotNull AyaLanguageClient client) {
    if (CLIENT == null) synchronized (Log.class) {
      if (CLIENT == null) CLIENT = client;
        // if the code was right, this should never happen
      else throw new Panic("double initialization occurred");
    }
    i("Log file: %s", LOG_FILE);
  }

  public static void i(@NotNull @PrintFormat String fmt, Object... args) { log(ShowMessageType.Info, fmt, args); }
  public static void e(@NotNull @PrintFormat String fmt, Object... args) { log(ShowMessageType.Error, fmt, args); }
  public static void w(@NotNull @PrintFormat String fmt, Object... args) { log(ShowMessageType.Warning, fmt, args); }
  public static void d(@NotNull @PrintFormat String fmt, Object... args) { log(ShowMessageType.Log, fmt, args); }

  public static void stackTrace(@NotNull Exception ex) {
    var s = new StringWriter();
    ex.printStackTrace(new PrintWriter(s));
    logUnformatted(ShowMessageType.Error, s.toString());
  }

  public static void log(@NotNull ShowMessageType type, @NotNull String fmt, Object... args) {
    logUnformatted(type, fmt.formatted(args));
  }

  public static void logUnformatted(@NotNull ShowMessageType type, String format) {
    logConsole(type, format);
    var client = CLIENT;
    if (client != null) client.logMessage(new ShowMessageParams(type.value, format));
  }

  public static void logConsole(@NotNull ShowMessageType type, @NotNull String content) {
    try {
      var format = String.format("[%s]: %s%n", type, content);
      System.err.print(format);
      Files.writeString(LOG_FILE, format,
        StandardCharsets.UTF_8,
        StandardOpenOption.APPEND);
    } catch (IOException _) {
    }
  }
}
