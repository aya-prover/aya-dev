// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.repl.command;

import kala.collection.immutable.ImmutableSeq;
import org.aya.cli.repl.AbstractRepl;
import org.jetbrains.annotations.NotNull;

public class EnableUnicodeCommand implements Command {
  private EnableUnicodeCommand() {
  }

  public static final EnableUnicodeCommand INSTANCE = new EnableUnicodeCommand();

  @Override public @NotNull ImmutableSeq<String> names() {
    return ImmutableSeq.of("unicode");
  }

  @Override
  public @NotNull String description() {
    return "Enable or disable unicode in REPL output";
  }

  @Override
  public @NotNull CommandExecutionResult execute(@NotNull String argument, @NotNull AbstractRepl repl) {
    var enableUnicode = Boolean.parseBoolean(argument.trim());
    repl.enableUnicode = enableUnicode;
    return CommandExecutionResult.successful("Unicode " + (enableUnicode ? "enabled" : "disabled"), true);
  }
}
