// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck.pat;

import org.aya.concrete.Expr;
import org.aya.core.def.Def;
import org.aya.core.term.Term;
import org.aya.generic.Pat;
import org.aya.tyck.ExprTycker;
import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000
 */
public record PatTycker(@NotNull ExprTycker exprTycker) implements
  Pat.Clause.Visitor<Expr, Def.Signature, Pat.Clause<Term>>,
  Pat.Visitor<Expr, Def.Signature, Pat.Clause<Term>> {
  @Override
  public Pat.Clause<Term> visitMatch(Pat.Clause.@NotNull Match<Expr> match, Def.Signature signature) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Pat.Clause<Term> visitAbsurd(Pat.Clause.@NotNull Absurd<Expr> absurd, Def.Signature signature) {
    return new Pat.Clause.Absurd<>();
  }

  @Override
  public Pat.Clause<Term> visitAtomic(Pat.@NotNull Atomic<Expr> atomic, Def.Signature signature) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Pat.Clause<Term> visitCtor(Pat.@NotNull Ctor<Expr> ctor, Def.Signature signature) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Pat.Clause<Term> visitUnresolved(Pat.@NotNull Unresolved<Expr> unresolved, Def.Signature signature) {
    throw new UnsupportedOperationException();
  }
}
