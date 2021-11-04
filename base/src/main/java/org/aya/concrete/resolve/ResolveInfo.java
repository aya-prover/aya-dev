// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.resolve;

import org.aya.concrete.desugar.BinOpSet;
import org.aya.concrete.stmt.Decl;
import org.aya.util.MutableGraph;
import org.jetbrains.annotations.NotNull;

public record ResolveInfo(
  @NotNull BinOpSet opSet,
  @NotNull MutableGraph<Decl> deps
) {
}
