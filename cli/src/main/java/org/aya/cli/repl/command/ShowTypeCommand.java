// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.repl.command;

import org.aya.cli.repl.AbstractRepl;
import org.jetbrains.annotations.NotNull;

public class ShowTypeCommand implements SingleLongNameWithFirstLetterShortNameCommand {
  private ShowTypeCommand() {
  }

  public static final ShowTypeCommand INSTANCE = new ShowTypeCommand();

  @Override
  public @NotNull String longName() {
    return "type";
  }

  @Override
  public @NotNull String description() {
    return "Show the type of the given expression";
  }

  @Override
  public @NotNull CommandExecutionResult execute(@NotNull String argument, @NotNull AbstractRepl repl) {
    var type = repl.replCompiler.compileExprAndGetType(argument, repl.normalizeMode);
    return type != null ? CommandExecutionResult.successful(repl.render(type), true) :
      CommandExecutionResult.failed("Failed to get expression type", true);
  }
}
