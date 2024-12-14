// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.free;

import org.aya.compiler.free.data.LocalVariable;
import org.jetbrains.annotations.NotNull;

public interface ArgumentProvider {
  interface Lambda extends ArgumentProvider {
    @NotNull FreeJavaExpr capture(int nth);
  }

  @NotNull LocalVariable arg(int nth);
}
