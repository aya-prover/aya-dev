// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.repl.jline.completer;

import kala.collection.immutable.ImmutableSeq;
import kala.control.Option;
import org.aya.cli.repl.Repl;
import org.aya.cli.repl.command.Command;
import org.aya.cli.repl.command.CommandManager;
import org.jetbrains.annotations.NotNull;
import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;

import java.util.List;

public record CmdCompleter(
  @NotNull Repl repl,
  @NotNull CommandManager cmd,
  @NotNull AyaCompleters.Code outerMost,
  @NotNull ImmutableSeq<Candidate> cmdNames
) implements Completer {
  public CmdCompleter(@NotNull Repl repl, @NotNull CommandManager cmd) {
    this(repl, cmd, new AyaCompleters.Code(repl),
      cmd.cmd.view().flatMap(c -> c.owner().names()).map(c -> Command.PREFIX + c)
        .map(Candidate::new).toImmutableSeq());
  }

  @Override
  public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
    if (line.wordIndex() == 0) {
      var word = line.word();
      if (word.startsWith(Command.PREFIX) || word.isEmpty())
        candidates.addAll(cmdNames.asJava());
    }
    var trim = line.line().trim();
    if (trim.startsWith(Command.PREFIX)) {
      // TODO: replace with Option.mapNotNull
      cmd.parse(trim.substring(1)).command()
        .getOption()
        .flatMap(c -> Option.of(c.argFactory()))
        .flatMap(arg -> Option.of(arg.completer()))
        .forEach(completer -> completer.complete(reader, line, candidates));
    } else outerMost.complete(reader, line, candidates);
  }
}
