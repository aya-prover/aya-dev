// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.def;

import org.aya.api.ref.DefVar;
import org.aya.concrete.Decl;
import org.aya.core.term.Term;
import org.aya.core.term.UnivTerm;
import org.glavo.kala.collection.Map;
import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.glavo.kala.tuple.Tuple;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000
 */
public final record PrimDef(
  @NotNull ImmutableSeq<Term.Param> telescope,
  @NotNull Term result,
  @NotNull DefVar<@NotNull PrimDef, Decl.PrimDecl> ref
) implements Def {
  public PrimDef(
    @NotNull ImmutableSeq<Term.Param> telescope,
    @NotNull Term result,
    @NotNull String name
  ) {
    //noinspection ConstantConditions
    this(telescope, result, DefVar.core(null, name));
    ref.core = this;
  }

  @Override public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
    return visitor.visitPrim(this, p);
  }

  public static final @NotNull PrimDef INTERVAL = new PrimDef(ImmutableSeq.empty(), UnivTerm.OMEGA, "I");

  @Override public @NotNull ImmutableSeq<Term.Param> contextTele() {
    return ImmutableSeq.empty();
  }

  public static final @NotNull Map<@NotNull String, DefVar<? extends PrimDef, Decl.PrimDecl>> primitives = Map.ofEntries(
    Tuple.of("I", INTERVAL.ref)
  );

  public @ApiStatus.Internal static void clearConcrete() {
    for (var var : primitives.valuesView()) var.concrete = null;
  }
}
