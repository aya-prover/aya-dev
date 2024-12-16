// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.def;

import org.aya.syntax.compile.JitMatchy;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.core.term.call.MatchCall;
import org.jetbrains.annotations.NotNull;

public sealed interface MatchyLike extends AnyDef permits JitMatchy, Matchy {
  @NotNull Term type(@NotNull MatchCall data);
}
