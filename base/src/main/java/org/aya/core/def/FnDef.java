// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.def;

import org.aya.api.ref.DefVar;
import org.aya.concrete.Decl;
import org.aya.core.pat.Pat;
import org.aya.core.sort.Sort;
import org.aya.core.term.Term;
import org.aya.generic.Matching;
import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.glavo.kala.control.Either;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

/**
 * @author ice1000
 */
public final record FnDef(
  @NotNull DefVar<FnDef, Decl.FnDecl> ref,
  @NotNull ImmutableSeq<Term.Param> contextTele,

  @NotNull ImmutableSeq<Term.Param> telescope,
  @NotNull ImmutableSeq<Sort.LvlVar> levels,
  @NotNull Term result,
  @NotNull Either<Term, ImmutableSeq<Matching<Pat, Term>>> body
) implements Def {
  public FnDef {
    ref.core = this;
  }

  public static @NotNull <T> Function<Either<Term, ImmutableSeq<Matching<Pat, Term>>>, T> factory(
    Function<Either<Term, ImmutableSeq<Matching<Pat, Term>>>, T> function
  ) {
    return function;
  }

  @Override public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
    return visitor.visitFn(this, p);
  }
}
