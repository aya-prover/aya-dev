// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.repl;

import kala.collection.immutable.ImmutableSeq;
import org.jetbrains.annotations.NotNull;
import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;

import java.util.List;

public record CmdCompleter(
  @NotNull CommandManager cmd,
  @NotNull Completer outerMost,
  @NotNull ImmutableSeq<Candidate> cmdNames
) implements Completer {
  public CmdCompleter(@NotNull CommandManager cmd, @NotNull Completer outerMost) {
    this(cmd, outerMost,
      cmd.cmd.view()
        .flatMap(c -> c.owner().names())
        .map(c -> Command.PREFIX + c)
        .map(Candidate::new)
        .toImmutableSeq());
  }

  @Override public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
    if (line.wordIndex() == 0) {
      var word = line.word();
      if (word.startsWith(Command.PREFIX) || word.isEmpty())
        candidates.addAll(cmdNames.asJava());
    }
    var trim = line.line().trim();
    if (trim.startsWith(Command.PREFIX)) {
      cmd.parse(trim.substring(1)).command().view()
        .mapNotNull(CommandManager.CommandGen::argFactory)
        .mapNotNull(CommandArg::completer)
        .forEach(completer -> completer.complete(reader, line, candidates));
    } else outerMost.complete(reader, line, candidates);
  }
}
