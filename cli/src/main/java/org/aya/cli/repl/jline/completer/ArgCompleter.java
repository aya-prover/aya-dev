// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.repl.jline.completer;

import org.aya.cli.repl.command.Command;
import org.aya.cli.repl.command.CommandManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;

import java.util.List;
import java.util.stream.Stream;

public interface ArgCompleter extends Completer {
  @NotNull CommandManager manager();
  @NotNull List<Candidate> KEYWORDS = Stream.of(
    "open", "data", "def", "Pi", "Sig", "Type", "universe",
    "tighter", "looser", "example", "counterexample", "lsuc", "lmax",
    "infix", "infixl", "infixr", "impossible", "prim", "struct", "new"
  ).map(Candidate::new).toList();

  record Keywords(@NotNull CommandManager manager) implements ArgCompleter {
    @Override public void complete(@Nullable Command command, List<Candidate> candidates) {
      if (command == null || command instanceof Command.CodeCommand)
        candidates.addAll(KEYWORDS);
    }
  }

  record Strings(@NotNull CommandManager manager) implements ArgCompleter {
    @Override public void complete(@Nullable Command command, List<Candidate> candidates) {
      if (command instanceof Command.StringCommand stringCommand)
        stringCommand.params().forEach(candidates::add);
    }
  }

  /** @param command null if no command (so the input should be Aya code) */
  void complete(@Nullable Command command, List<Candidate> candidates);
  @Override default void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
    var trim = line.line().trim();
    if (trim.startsWith(Command.PREFIX)) {
      var life = manager().parse(trim.substring(1)).command();
      if (life.isDefined()) complete(life.get(), candidates);
    } else complete(null, candidates);
  }
}
