// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.repl.command;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import org.aya.cli.repl.Repl;
import org.aya.pretty.doc.Doc;
import org.jetbrains.annotations.NotNull;
import org.jline.reader.Candidate;

public interface Command {
  interface StringCommand extends Command {
    SeqView<Candidate> params();
  }
  interface CodeCommand extends Command {
  }

  @NotNull String PREFIX = ":";

  @NotNull ImmutableSeq<String> names();
  @NotNull String description();

  /**
   * Execute the command.
   *
   * @param argument the command content such as args and code with the command prefix removed
   * @param repl     the REPL
   * @return the result
   */
  @NotNull Command.Result execute(@NotNull String argument, @NotNull Repl repl);

  record Output(@NotNull Doc stdout, @NotNull Doc stderr) {
    public static @NotNull Output stdout(@NotNull Doc doc) {
      return new Output(doc, Doc.empty());
    }

    public static @NotNull Output stderr(@NotNull Doc doc) {
      return new Output(Doc.empty(), doc);
    }

    public static @NotNull Output stdout(@NotNull String doc) {
      return new Output(Doc.english(doc), Doc.empty());
    }

    public static @NotNull Output stderr(@NotNull String doc) {
      return new Output(Doc.empty(), Doc.english(doc));
    }
  }

  record Result(@NotNull Output output, boolean continueRepl) {
    public static @NotNull Command.Result ok(@NotNull String text, boolean continueRepl) {
      return new Result(Output.stdout(Doc.english(text)), continueRepl);
    }

    public static @NotNull Command.Result err(@NotNull String errText, boolean continueRepl) {
      return new Result(Output.stderr(Doc.english(errText)), continueRepl);
    }
  }
}
