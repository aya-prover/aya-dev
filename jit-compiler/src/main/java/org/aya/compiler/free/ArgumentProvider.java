// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.free;

import org.jetbrains.annotations.NotNull;

public interface ArgumentProvider {
  interface Lambda extends ArgumentProvider {
    @NotNull FreeJava capture(int nth);
  }

  @NotNull FreeJava arg(int nth);
}
