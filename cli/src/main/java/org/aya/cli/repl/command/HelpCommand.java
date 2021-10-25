// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.repl.command;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import org.aya.cli.repl.Repl;
import org.aya.pretty.doc.Doc;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jline.reader.Candidate;

public class HelpCommand implements Command.StringCommand {
  public @Nullable CommandManager context;

  @Override public @NotNull ImmutableSeq<String> names() {
    return ImmutableSeq.of("help", "h", "?");
  }

  @Override public @NotNull String description() {
    return "Describe a selected command or show all commands";
  }

  @Override public SeqView<Candidate> params() {
    if (context == null) return SeqView.empty();
    return context.commands.view().flatMap(Command::names).map(Candidate::new);
  }

  @Override
  public @NotNull Command.Result execute(@NotNull String argument, @NotNull Repl repl) {
    if (!argument.isEmpty()) {
      var cmd = repl.commandManager.commands.find(command -> command.names().contains(argument));
      if (cmd.isDefined()) return Result.ok(cmd.get().description(), true);
      else return Result.err("No such command: " + argument, true);
    }
    var commands = Doc.vcat(repl.commandManager.commands.view()
      .map(command -> Doc.sep(
        Doc.commaList(command.names().map(name -> Doc.plain(Command.PREFIX + name))),
        Doc.english(command.description())
      )));

    return new Result(new Output(commands, Doc.empty()), true);
  }
}
