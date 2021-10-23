// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.repl.command;

import kala.collection.immutable.ImmutableSeq;
import org.aya.api.util.NormalizeMode;
import org.aya.cli.repl.AbstractRepl;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public class SetNormalizeModeCommand implements Command {
  private SetNormalizeModeCommand() {
  }

  public static final SetNormalizeModeCommand INSTANCE = new SetNormalizeModeCommand();

  @Override public @NotNull ImmutableSeq<String> names() {
    return ImmutableSeq.of("normalize");
  }

  @Override public @NotNull String description() {
    return "Set the normalize mode (candidates: " + Arrays.toString(NormalizeMode.values()) + ")";
  }

  @Override
  public @NotNull CommandExecutionResult execute(@NotNull String argument, @NotNull AbstractRepl repl) {
    var normalizeMode = NormalizeMode.valueOf(argument.trim());
    repl.normalizeMode = normalizeMode;
    return CommandExecutionResult.successful("Normalize mode set to " + normalizeMode, true);
  }
}
