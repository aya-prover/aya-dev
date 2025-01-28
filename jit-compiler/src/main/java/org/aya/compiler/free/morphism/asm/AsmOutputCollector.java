// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.free.morphism.asm;

import org.jetbrains.annotations.NotNull;

import java.lang.constant.ClassDesc;
import java.nio.file.Path;

public interface AsmOutputCollector {
  void write(@NotNull ClassDesc className, byte @NotNull [] bytecode);

  record AsmClassFileOutputCollector(@NotNull Path baseDir) implements AsmOutputCollector {
    @Override
    public void write(@NotNull ClassDesc className, byte @NotNull [] bytecode) {
      // TODO
    }
  }
}
