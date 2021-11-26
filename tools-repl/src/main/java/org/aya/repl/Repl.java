// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.repl;

import org.aya.pretty.doc.Doc;
import org.jetbrains.annotations.NotNull;
import org.jline.reader.EndOfFileException;
import org.jline.reader.UserInterruptException;

import java.io.PrintWriter;
import java.io.StringWriter;

public interface Repl {
  void println(@NotNull String x);
  void errPrintln(@NotNull String x);
  @NotNull String readLine(@NotNull String prompt) throws EndOfFileException, UserInterruptException;
  @NotNull Command.Output eval(@NotNull String line);

  default void printResult(@NotNull Command.Output output) {
    if (output.stdout().isNotEmpty()) println(renderDoc(output.stdout()));
    if (output.stderr().isNotEmpty()) errPrintln(renderDoc(output.stderr()));
  }

  default boolean loop(@NotNull String prompt, @NotNull CommandManager commandManager) {
    try {
      var line = readLine(prompt).trim();
      if (line.startsWith(Command.MULTILINE_BEGIN) && line.endsWith(Command.MULTILINE_END)) {
        var code = line.substring(Command.MULTILINE_BEGIN.length(), line.length() - Command.MULTILINE_END.length());
        printResult(eval(code));
      } else if (line.startsWith(Command.PREFIX)) {
        var result = commandManager.parse(line.substring(1)).run(this);
        printResult(result.output());
        return result.continueRepl();
      } else printResult(eval(line));
      // } catch (InterruptException ignored) {
      // ^ already caught by `eval`
    } catch (EndOfFileException ignored) {
      // user send ctrl-d
      return false;
    } catch (UserInterruptException ignored) {
      // user send ctrl-c
    } catch (Throwable e) {
      var stackTrace = new StringWriter();
      e.printStackTrace(new PrintWriter(stackTrace));
      errPrintln(stackTrace.toString());
    }
    return true;
  }

  default @NotNull String renderDoc(@NotNull Doc doc) {
    return doc.debugRender();
  }
}
