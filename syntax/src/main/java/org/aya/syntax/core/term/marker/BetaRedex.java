// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.term.marker;

import org.aya.syntax.core.annotation.Closed;
import org.aya.syntax.core.term.AppTerm;
import org.aya.syntax.core.term.LetTerm;
import org.aya.syntax.core.term.ProjTerm;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.core.term.call.MemberCall;
import org.aya.syntax.core.term.xtt.PAppTerm;
import org.jetbrains.annotations.NotNull;

import java.util.function.UnaryOperator;

public sealed interface BetaRedex extends Term permits AppTerm, LetTerm, ProjTerm, MemberCall, PAppTerm {
  /// @apiNote `this` is required to be [Closed]
  @Closed @NotNull Term make(@NotNull UnaryOperator<@Closed Term> mapper);
  default @Closed @NotNull Term make() { return make(UnaryOperator.identity()); }
}
