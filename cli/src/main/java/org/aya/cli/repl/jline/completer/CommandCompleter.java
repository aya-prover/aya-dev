// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.repl.jline.completer;

import kala.collection.immutable.ImmutableSeq;
import org.aya.cli.repl.command.Command;
import org.jetbrains.annotations.NotNull;
import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;

import java.util.List;

public class CommandCompleter implements Completer {
  public final @NotNull ImmutableSeq<Candidate> candidates;

  public CommandCompleter(@NotNull ImmutableSeq<Command> commands) {
    candidates = commands.flatMap(command -> command.names().map(name -> new Candidate(":" + name)));
  }

  @Override
  public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
    if (!line.line().startsWith(":")) return;
    candidates.addAll(this.candidates.asJava());
  }
}
