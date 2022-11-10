// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.visitor;

import org.aya.core.term.RefTerm;
import org.aya.core.term.Term;
import org.aya.guest0x0.cubical.RestrSimplifier;
import org.aya.ref.LocalVar;
import org.jetbrains.annotations.Nullable;

public final class AyaRestrSimplifier implements RestrSimplifier<Term, LocalVar> {
  public static final AyaRestrSimplifier INSTANCE = new AyaRestrSimplifier();

  private AyaRestrSimplifier() {}

  @Override public @Nullable LocalVar asRef(Term term) {
    return term instanceof RefTerm(var ref) ? ref : null;
  }
}
