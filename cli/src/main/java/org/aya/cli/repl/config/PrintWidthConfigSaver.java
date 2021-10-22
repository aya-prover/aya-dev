// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.repl.config;

import org.aya.cli.repl.AbstractRepl;
import org.jetbrains.annotations.NotNull;

public class PrintWidthConfigSaver implements ConfigSaver {
  private PrintWidthConfigSaver() {
  }

  public static final PrintWidthConfigSaver INSTANCE = new PrintWidthConfigSaver();

  @Override
  public @NotNull String configKey() {
    return "repl_print_width";
  }

  @Override
  public void deserializeAndSet(@NotNull AbstractRepl repl, @NotNull String serializedConfig) {
    repl.prettyPrintWidth = Integer.parseInt(serializedConfig);
  }

  @Override
  public @NotNull String getAndSerialize(@NotNull AbstractRepl repl) {
    return Integer.toString(repl.prettyPrintWidth);
  }
}
