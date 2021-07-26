// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.concrete.stmt;

import kala.tuple.Tuple;
import kala.tuple.Tuple2;
import org.aya.api.util.Assoc;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface OpDecl {
  @Nullable Tuple2<@Nullable String, @NotNull Assoc> asOperator();

  @NotNull String APP_NAME = "application";
  @NotNull OpDecl APP = () -> Tuple.of(APP_NAME, Assoc.Infix);
}
