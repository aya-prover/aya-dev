// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.repl;

import org.aya.pretty.doc.Doc;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

public interface ReplUtil {
  static @NotNull Command.Result invokeHelp(CommandManager commandManager, @Nullable HelpItem argument) {
    if (argument != null && !argument.cmd.isEmpty()) {
      var cmd = commandManager.cmd.find(c -> c.owner().names().contains(argument.cmd));
      if (cmd.isDefined()) return Command.Result.ok(cmd.get().owner().help(), true);
      else return Command.Result.err("No such command: " + argument.cmd, true);
    }
    var commands = Doc.vcat(commandManager.cmd.view()
      .map(command -> Doc.sep(
        Doc.commaList(command.owner().names().map(name -> Doc.plain(Command.PREFIX + name))),
        Doc.plain("-"),
        Doc.english(command.owner().help())
      )));
    return new Command.Result(new Command.Output(commands, Doc.empty()), true);
  }

  record HelpItem(@NotNull String cmd) {
  }

  static @NotNull String red(@NotNull String x) {
    return new AttributedStringBuilder()
      .style(AttributedStyle.DEFAULT.foreground(AttributedStyle.RED))
      .append(x)
      .style(AttributedStyle.DEFAULT)
      .toAnsi();
  }
}
