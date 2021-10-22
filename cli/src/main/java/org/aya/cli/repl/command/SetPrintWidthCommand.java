// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.repl.command;

import org.aya.cli.repl.AbstractRepl;
import org.jetbrains.annotations.NotNull;

public class SetPrintWidthCommand implements SingleLongNameCommand, NoShortNameCommand {
  private SetPrintWidthCommand() {
  }

  public static final SetPrintWidthCommand INSTANCE = new SetPrintWidthCommand();

  @Override
  public @NotNull String longName() {
    return "print-width";
  }

  @Override
  public @NotNull String description() {
    return "Set printed output width";
  }

  @Override
  public @NotNull CommandExecutionResult execute(@NotNull String argument, @NotNull AbstractRepl repl) {
    var prettyPrintWidth = Integer.parseInt(argument.trim());
    repl.prettyPrintWidth = prettyPrintWidth;
    return CommandExecutionResult.successful("Printed output width set to " + prettyPrintWidth, true);
  }
}
