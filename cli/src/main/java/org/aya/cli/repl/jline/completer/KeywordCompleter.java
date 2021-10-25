// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.repl.jline.completer;

import org.aya.cli.repl.command.Command;
import org.aya.cli.repl.command.CommandManager;
import org.jetbrains.annotations.NotNull;
import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;

import java.util.List;
import java.util.stream.Stream;

public record KeywordCompleter(@NotNull CommandManager manager) implements Completer {
  private static final @NotNull List<Candidate> KEYWORDS = Stream.of(
    "open", "data", "def", "Pi", "Sig", "Type", "universe",
    "tighter", "looser", "example", "counterexample", "lsuc", "lmax",
    "infix", "infixl", "infixr", "impossible", "prim", "struct", "new"
  ).map(Candidate::new).toList();

  @Override public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
    var trim = line.line().trim();
    if (trim.startsWith(Command.PREFIX)) {
      var life = manager.parse(trim).command();
      if (life.isDefined() && life.get() instanceof Command.CodeCommand)
        candidates.addAll(KEYWORDS);
    } else candidates.addAll(KEYWORDS);
  }
}
