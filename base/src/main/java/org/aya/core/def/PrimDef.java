// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.def;

import org.aya.api.ref.DefVar;
import org.aya.api.ref.LocalVar;
import org.aya.api.util.Arg;
import org.aya.concrete.Decl;
import org.aya.core.term.*;
import org.aya.util.Constants;
import org.glavo.kala.collection.Map;
import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.glavo.kala.tuple.Tuple;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

/**
 * @author ice1000
 */
public final record PrimDef(
  @NotNull ImmutableSeq<Term.Param> telescope,
  @NotNull Term result,
  @NotNull Function<CallTerm.@NotNull Prim, @NotNull Term> unfold,
  @NotNull DefVar<@NotNull PrimDef, Decl.PrimDecl> ref
) implements Def {
  public PrimDef(
    @NotNull ImmutableSeq<Term.Param> telescope,
    @NotNull Term result,
    @NotNull Function<CallTerm.@NotNull Prim, @NotNull Term> unfold,
    @NotNull String name
  ) {
    //noinspection ConstantConditions
    this(telescope, result, unfold, DefVar.core(null, name));
    ref.core = this;
  }

  private static @NotNull Term arcoe(@NotNull CallTerm.Prim prim) {
    var args = prim.args();
    var argBase = args.get(1).term();
    var argI = args.get(2).term();
    if (argI instanceof CallTerm.Prim primCall && primCall.ref() == LEFT.ref) return argBase;
    var argA = args.get(0).term();
    if (argA instanceof LamTerm lambda && lambda.body().findUsages(lambda.param().ref()) == 0) return argBase;
    return prim;
  }

  @Override public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
    return visitor.visitPrim(this, p);
  }

  public static final @NotNull PrimDef INTERVAL = new PrimDef(ImmutableSeq.empty(), UnivTerm.OMEGA, prim -> prim, "I");
  public static final @NotNull CallTerm.Prim INTERVAL_CALL = new CallTerm.Prim(INTERVAL.ref, ImmutableSeq.of());
  public static final @NotNull PrimDef LEFT = new PrimDef(ImmutableSeq.empty(), INTERVAL_CALL, prim -> prim, "left");
  public static final @NotNull PrimDef RIGHT = new PrimDef(ImmutableSeq.empty(), INTERVAL_CALL, prim -> prim, "right");

  /** Short for <em>Arend coe</em>. */
  public static final @NotNull PrimDef ARCOE;

  static {
    var paramA = new LocalVar("A");
    var paramI = new LocalVar("i");
    var paramIToATy = new Term.Param(new LocalVar(Constants.ANONYMOUS_PREFIX), INTERVAL_CALL, true);
    var baseAtLeft = new AppTerm(new RefTerm(paramA), Arg.explicit(new CallTerm.Prim(LEFT.ref, ImmutableSeq.empty())));
    ARCOE = new PrimDef(
      ImmutableSeq.of(
        new Term.Param(paramA, new PiTerm(false, paramIToATy, UnivTerm.OMEGA), true),
        new Term.Param(new LocalVar("base"), baseAtLeft, true),
        new Term.Param(paramI, INTERVAL_CALL, true)
      ),
      new AppTerm(new RefTerm(paramA), Arg.explicit(new RefTerm(paramI))),
      PrimDef::arcoe, "arcoe");
  }

  public static final @NotNull ImmutableSeq<PrimDef> LEFT_RIGHT = ImmutableSeq.of(PrimDef.LEFT, PrimDef.RIGHT);

  public @NotNull Term unfold(@NotNull CallTerm.Prim primCall) {
    return unfold.apply(primCall);
  }

  @Override public @NotNull ImmutableSeq<Term.Param> telescope() {
    if (telescope.isEmpty()) return telescope;
    if (ref.concrete != null) {
      var signature = ref.concrete.signature;
      if (signature != null) return signature.param();
    }
    return telescope;
  }

  @Override public @NotNull Term result() {
    if (ref.concrete != null) {
      var signature = ref.concrete.signature;
      if (signature != null) return signature.result();
    }
    return result;
  }

  @Override public @NotNull ImmutableSeq<Term.Param> contextTele() {
    return ImmutableSeq.empty();
  }

  public static final @NotNull Map<@NotNull String, @NotNull PrimDef> PRIMITIVES = ImmutableSeq
    .of(INTERVAL, LEFT, RIGHT, ARCOE).view()
    .map(prim -> Tuple.of(prim.ref.name(), prim))
    .toImmutableMap();

  public @ApiStatus.Internal static void clearConcrete() {
    for (var var : PRIMITIVES.valuesView()) var.ref.concrete = null;
  }
}
