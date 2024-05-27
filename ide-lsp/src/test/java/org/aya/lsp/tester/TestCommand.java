// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.lsp.tester;

import kala.tuple.Unit;
import org.jetbrains.annotations.NotNull;

public sealed interface TestCommand {
  @FunctionalInterface
  interface Checker<T> {
    void check(@NotNull LspTestCompilerAdvisor advisor, T extra);
  }

  @NotNull Checker<?> checker();

  record Mutate(@NotNull String moduleName, @NotNull Checker<Unit> checker) implements TestCommand {}

  record Compile(@NotNull Checker<Long> checker) implements TestCommand {}

  static @NotNull Mutate mutate(@NotNull String moduleName, @NotNull Checker<Unit> checker) {
    return new Mutate(moduleName, checker);
  }

  static @NotNull Mutate mutate(@NotNull String moduleName) {
    return mutate(moduleName, (a, b) -> {});
  }

  static @NotNull Compile compile(@NotNull Checker<Long> checker) {
    return new Compile(checker);
  }
}
