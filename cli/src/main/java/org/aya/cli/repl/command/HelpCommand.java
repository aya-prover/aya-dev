// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.repl.command;

import kala.collection.immutable.ImmutableSeq;
import org.aya.cli.repl.Repl;
import org.aya.pretty.doc.Doc;
import org.jetbrains.annotations.NotNull;

public record HelpCommand(@NotNull ImmutableSeq<String> names, @NotNull String description) implements Command {
  public static final HelpCommand INSTANCE = new HelpCommand(
    ImmutableSeq.of("help", "h", "?"), "Show command help");

  @Override
  public @NotNull Command.Result execute(@NotNull String argument, @NotNull Repl repl) {
    var commands = Doc.vcat(repl.commandManager.commands.view()
      .map(command -> Doc.sep(
        Doc.commaList(command.names().map(name -> Doc.plain(Command.PREFIX + name))),
        Doc.english(command.description())
      )));

    return new Result(new Output(Doc.vcat(
      Doc.english("REPL commands:"), commands), Doc.empty()), true);
  }
}
