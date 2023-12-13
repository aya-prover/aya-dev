// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.repl;

import org.aya.pretty.doc.Doc;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

import java.nio.file.Path;

public interface ReplUtil {
  static @NotNull Command.Result invokeHelp(CommandManager commandManager, @Nullable HelpItem argument) {
    if (argument != null && !argument.cmd.isEmpty()) {
      return commandManager.cmd.find(c -> c.owner().names().contains(argument.cmd))
        .getOrElse(
          it -> Command.Result.ok(it.owner().help(), true),
          () -> Command.Result.err(STR."No such command: \{argument.cmd}", true));
    }
    var commands = Doc.vcat(commandManager.cmd.view()
      .map(command -> Doc.sep(
        Doc.commaList(command.owner().names().map(name -> Doc.plain(Command.PREFIX + name))),
        Doc.symbol("-"),
        Doc.english(command.owner().help())
      )));
    return new Command.Result(new Command.Output(commands, Doc.empty()), true);
  }

  record HelpItem(@NotNull String cmd) {
  }

  static @NotNull Path resolveFile(@NotNull String arg, Path cwd) {
    var homeAware = arg.replaceFirst("^~", System.getProperty("user.home"));
    var path = Path.of(homeAware);
    return path.isAbsolute() ? path.normalize() : cwd.resolve(homeAware).toAbsolutePath().normalize();
  }

  static @NotNull String red(@NotNull String x) {
    return new AttributedStringBuilder()
      .style(AttributedStyle.DEFAULT.foreground(AttributedStyle.RED))
      .append(x)
      .style(AttributedStyle.DEFAULT)
      .toAnsi();
  }
}
