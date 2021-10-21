// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.repl.command;

import org.aya.api.util.NormalizeMode;
import org.aya.cli.repl.AbstractRepl;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public class SetNormalizeModeCommand implements SingleLongNameCommand, NoShortNameCommand {
  private SetNormalizeModeCommand() {
  }

  public static final SetNormalizeModeCommand INSTANCE = new SetNormalizeModeCommand();

  @Override
  public @NotNull String longName() {
    return "normalize";
  }

  @Override
  public @NotNull String description() {
    return "Set the normalize mode (candidates: " + Arrays.toString(NormalizeMode.values()) + ")";
  }

  @Override
  public @NotNull CommandExecutionResult execute(@NotNull String argument, @NotNull AbstractRepl repl) {
    var normalizeMode = NormalizeMode.valueOf(argument.trim());
    repl.normalizeMode = normalizeMode;
    return CommandExecutionResult.successful("Normalize mode set to " + normalizeMode, true);
  }
}
