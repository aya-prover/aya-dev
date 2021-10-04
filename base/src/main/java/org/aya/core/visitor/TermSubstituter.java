// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.visitor;

import kala.collection.Map;
import kala.tuple.Unit;
import org.aya.api.ref.Var;
import org.aya.core.term.RefTerm;
import org.aya.core.term.Term;
import org.jetbrains.annotations.NotNull;

/** Replaces var (probably just LocalVar and DefVar for fields) with terms. */
public interface TermSubstituter extends TermFixpoint<Unit> {
  Map<Var, Term> termSubst();

  @Override default @NotNull Term visitFieldRef(@NotNull RefTerm.Field field, Unit unit) {
    return termSubst().getOrDefault(field.ref(), field);
  }

  @Override default @NotNull Term visitRef(@NotNull RefTerm ref, Unit unused) {
    return termSubst().getOrElse(ref.var(), () ->
      TermFixpoint.super.visitRef(ref, Unit.unit()));
  }
}
