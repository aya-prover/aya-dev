// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.core;

import org.glavo.kala.Tuple3;
import org.glavo.kala.Unit;
import org.glavo.kala.collection.Seq;
import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.glavo.kala.collection.mutable.Buffer;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;
import org.mzi.api.ref.Bind;
import org.mzi.api.ref.Var;
import org.mzi.core.term.AppTerm;
import org.mzi.core.term.Term;
import org.mzi.core.visitor.Substituter;
import org.mzi.generic.Arg;
import org.mzi.generic.Param;
import org.mzi.ref.LocalVar;
import org.mzi.tyck.sort.LevelSubst;

/**
 * @author re-xyr
 */
public record CoreParam(
  @NotNull Var ref,
  @NotNull Term type,
  boolean explicit
) implements Bind, Param<Term> {
  public static @NotNull ImmutableSeq<@NotNull CoreParam> fromBuffer(Buffer<Tuple3<Var, Boolean, Term>> buf) {
    return buf.toImmutableSeq().map(tup -> new CoreParam(tup._1, tup._3, tup._2));
  }

  public @NotNull CoreParam subst(@NotNull Var var, @NotNull Term term) {
    return subst(new Substituter.TermSubst(var, term));
  }

  public @NotNull CoreParam subst(@NotNull Substituter.TermSubst subst) {
    return subst(subst, LevelSubst.EMPTY);
  }

  public @NotNull CoreParam subst(@NotNull Substituter.TermSubst subst, @NotNull LevelSubst levelSubst) {
    return new CoreParam(ref, type.accept(new Substituter(subst, levelSubst), Unit.unit()), explicit);
  }

  public static @NotNull CoreParam mock(@NotNull Var hole, boolean explicit) {
    return new CoreParam(new LocalVar("_"), new AppTerm.HoleApp(hole), explicit);
  }

  @TestOnly @Contract(pure = true)
  public static boolean checkSubst(@NotNull Seq<@NotNull CoreParam> params, @NotNull Seq<@NotNull ? extends @NotNull Arg<? extends Term>> args) {
    var obj = new Object() {
      boolean ok = true;
    };
    params.forEachIndexed((i, param) -> obj.ok = obj.ok && param.explicit() == args.get(i).explicit());
    return obj.ok;
  }
}
