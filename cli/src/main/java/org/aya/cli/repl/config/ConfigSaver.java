// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.repl.config;

import org.aya.cli.repl.AbstractRepl;
import org.jetbrains.annotations.NotNull;

public interface ConfigSaver {
  @NotNull String configKey();
  void deserializeAndSet(@NotNull AbstractRepl repl, @NotNull String serializedConfig);
  @NotNull String getAndSerialize(@NotNull AbstractRepl repl);
}
