// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.repl.config;

import org.aya.api.util.NormalizeMode;
import org.aya.cli.repl.AbstractRepl;
import org.jetbrains.annotations.NotNull;

public class NormalizeModeConfigSaver implements ConfigSaver {
  private NormalizeModeConfigSaver() {
  }

  public static final NormalizeModeConfigSaver INSTNACE = new NormalizeModeConfigSaver();

  @Override
  public @NotNull String configKey() {
    return "repl_normalize_mode";
  }

  @Override
  public void deserializeAndSet(@NotNull AbstractRepl repl, @NotNull String serializedConfig) {
    repl.normalizeMode = NormalizeMode.valueOf(serializedConfig);
  }

  @Override
  public @NotNull String getAndSerialize(@NotNull AbstractRepl repl) {
    return repl.normalizeMode.name();
  }
}
