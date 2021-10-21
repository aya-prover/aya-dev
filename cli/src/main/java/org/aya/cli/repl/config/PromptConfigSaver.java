// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.repl.config;

import org.aya.cli.repl.AbstractRepl;
import org.jetbrains.annotations.NotNull;

public class PromptConfigSaver implements ConfigSaver {
  private PromptConfigSaver() {
  }

  public static final PromptConfigSaver INSTANCE = new PromptConfigSaver();

  @Override
  public @NotNull String configKey() {
    return "repl_prompt";
  }

  @Override
  public void deserializeAndSet(@NotNull AbstractRepl repl, @NotNull String serializedConfig) {
    repl.prompt = serializedConfig;
  }

  @Override
  public @NotNull String getAndSerialize(@NotNull AbstractRepl repl) {
    return repl.prompt;
  }
}
