// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.term.xtt;

import kala.function.IndexedFunction;
import org.aya.syntax.core.term.Term;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

public sealed interface CofElement permits SingleCof, TopCof, BotCof {
  @NotNull CofElement descent(@NotNull IndexedFunction<Term, Term> f);
  @NotNull CofElement map(Function<Term, Term> f);
}
