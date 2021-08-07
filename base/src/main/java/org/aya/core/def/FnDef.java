// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.def;

import kala.collection.immutable.ImmutableSeq;
import kala.control.Either;
import org.aya.api.ref.DefVar;
import org.aya.concrete.stmt.Decl;
import org.aya.core.Matching;
import org.aya.core.sort.Sort;
import org.aya.core.term.Term;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

/**
 * @author ice1000
 */
public final class FnDef implements Def {
  public final @NotNull DefVar<FnDef, Decl.FnDecl> ref;
  public final @NotNull ImmutableSeq<Term.Param> telescope;
  public final @NotNull ImmutableSeq<Sort.LvlVar> levels;
  public final @NotNull Term result;
  public final @NotNull Either<Term, ImmutableSeq<Matching>> body;

  public FnDef(@NotNull DefVar<FnDef, Decl.FnDecl> ref, @NotNull ImmutableSeq<Term.Param> telescope, @NotNull ImmutableSeq<Sort.LvlVar> levels, @NotNull Term result, @NotNull Either<Term, ImmutableSeq<Matching>> body) {
    ref.core = this;
    this.ref = ref;
    this.telescope = telescope;
    this.levels = levels;
    this.result = result;
    this.body = body;
  }

  public static @NotNull <T> Function<Either<Term, ImmutableSeq<Matching>>, T> factory(
    Function<Either<Term, ImmutableSeq<Matching>>, T> function
  ) {
    return function;
  }

  @Override public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
    return visitor.visitFn(this, p);
  }

  public @NotNull DefVar<FnDef, Decl.FnDecl> ref() {
    return ref;
  }

  public @NotNull ImmutableSeq<Term.Param> telescope() {
    return telescope;
  }

  public @NotNull ImmutableSeq<Sort.LvlVar> levels() {
    return levels;
  }

  public @NotNull Term result() {
    return result;
  }
}
