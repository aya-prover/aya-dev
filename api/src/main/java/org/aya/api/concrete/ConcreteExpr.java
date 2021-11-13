// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.api.concrete;

import org.aya.api.distill.AyaDocile;
import org.aya.api.error.Reporter;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.NonExtendable
public interface ConcreteExpr extends AyaDocile {
  @NotNull SourcePos sourcePos();
  @NotNull ConcreteExpr desugar(@NotNull Reporter reporter);
}
