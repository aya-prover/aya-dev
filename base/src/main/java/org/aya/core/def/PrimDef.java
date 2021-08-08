// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.def;

import kala.collection.Map;
import kala.collection.immutable.ImmutableMap;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableMap;
import kala.control.Option;
import kala.tuple.Tuple;
import org.aya.api.ref.DefVar;
import org.aya.api.ref.LevelGenVar;
import org.aya.api.ref.LocalVar;
import org.aya.api.util.Arg;
import org.aya.concrete.stmt.Decl;
import org.aya.core.sort.Sort;
import org.aya.core.term.*;
import org.aya.generic.Level;
import org.aya.util.Constants;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * @author ice1000
 */
public record PrimDef(
  @NotNull ImmutableSeq<Term.Param> telescope,
  @NotNull ImmutableSeq<Sort.LvlVar> levels,
  @NotNull Term result,
  @NotNull Function<CallTerm.@NotNull Prim, @NotNull Term> unfold,
  @NotNull DefVar<@NotNull PrimDef, Decl.PrimDecl> ref
) implements Def {
  public PrimDef(
    @NotNull ImmutableSeq<Term.Param> telescope,
    @NotNull ImmutableSeq<Sort.LvlVar> levels,
    @NotNull Term result,
    @NotNull Function<CallTerm.@NotNull Prim, @NotNull Term> unfold,
    @NotNull String name
  ) {
    //noinspection ConstantConditions
    this(telescope, levels, result, unfold, DefVar.core(null, name));
    ref.core = this;
  }

  private static @NotNull Term arcoe(@NotNull CallTerm.Prim prim) {
    var args = prim.args();
    var argBase = args.get(1).term();
    var argI = args.get(2).term();
    if (argI instanceof CallTerm.Prim primCall && primCall.ref() == LEFT.ref) return argBase;
    var argA = args.get(0).term();
    if (argA instanceof IntroTerm.Lambda lambda && lambda.body().findUsages(lambda.param().ref()) == 0) return argBase;
    return prim;
  }

  private static @NotNull Term invol(@NotNull CallTerm.Prim prim) {
    var arg = prim.args().get(0).term();
    if (arg instanceof CallTerm.Prim primCall) {
      if (primCall.ref() == LEFT.ref) return new CallTerm.Prim(LEFT.ref, ImmutableSeq.empty(), ImmutableSeq.empty());
      if (primCall.ref() == RIGHT.ref) return new CallTerm.Prim(LEFT.ref, ImmutableSeq.empty(), ImmutableSeq.empty());
    }
    return prim;
  }

  @Override public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
    return visitor.visitPrim(this, p);
  }

  public static final @NotNull PrimDef INTERVAL = new PrimDef(ImmutableSeq.empty(), ImmutableSeq.empty(),
    new FormTerm.Univ(new Sort(new Level.Constant<>(0), Sort.INF_LVL)), prim -> prim, "I");
  public static final @NotNull CallTerm.Prim INTERVAL_CALL = new CallTerm.Prim(INTERVAL.ref, ImmutableSeq.empty(), ImmutableSeq.empty());
  public static final @NotNull PrimDef LEFT = new PrimDef(ImmutableSeq.empty(), ImmutableSeq.empty(), INTERVAL_CALL, prim -> prim, "left");
  public static final @NotNull PrimDef RIGHT = new PrimDef(ImmutableSeq.empty(), ImmutableSeq.empty(), INTERVAL_CALL, prim -> prim, "right");
  public static final @NotNull CallTerm.Prim LEFT_CALL = new CallTerm.Prim(LEFT.ref, ImmutableSeq.empty(), ImmutableSeq.empty());
  public static final @NotNull CallTerm.Prim RIGHT_CALL = new CallTerm.Prim(RIGHT.ref, ImmutableSeq.empty(), ImmutableSeq.empty());

  public static final @NotNull PrimDef INVOL;

  static {
    var paramI = new LocalVar("i");
    INVOL = new PrimDef(ImmutableSeq.of(new Term.Param(paramI, INTERVAL_CALL, true)), ImmutableSeq.of(), INTERVAL_CALL, PrimDef::invol, "invol");
  }

  /** Short for <em>Arend coe</em>. */
  public static final @NotNull PrimDef ARCOE;

  static {
    var paramA = new LocalVar("A");
    var paramIToATy = new Term.Param(new LocalVar(Constants.ANONYMOUS_PREFIX), INTERVAL_CALL, true);
    var paramI = new LocalVar("i");
    var homotopy = new Sort.LvlVar("h", LevelGenVar.Kind.Homotopy, null);
    var universe = new Sort.LvlVar("u", LevelGenVar.Kind.Universe, null);
    var result = new FormTerm.Univ(new Sort(new Level.Reference<>(universe), new Level.Reference<>(homotopy)));
    var paramATy = new FormTerm.Pi(paramIToATy, result);
    var aRef = new RefTerm(paramA, paramATy);
    var baseAtLeft = new ElimTerm.App(aRef, Arg.explicit(new CallTerm.Prim(LEFT.ref, ImmutableSeq.of(), ImmutableSeq.empty())));
    ARCOE = new PrimDef(
      ImmutableSeq.of(
        new Term.Param(paramA, paramATy, true),
        new Term.Param(new LocalVar("base"), baseAtLeft, true),
        new Term.Param(paramI, INTERVAL_CALL, true)
      ),
      ImmutableSeq.of(homotopy, universe),
      new ElimTerm.App(aRef, Arg.explicit(new RefTerm(paramI, INTERVAL_CALL))),
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

  public static final @NotNull Map<@NotNull String, @NotNull PrimDef> PRIMITIVES = ImmutableSeq
    .of(INTERVAL, LEFT, RIGHT, ARCOE, INVOL).view()
    .map(prim -> Tuple.of(prim.ref.name(), prim))
    .toImmutableMap();

  public static final @NotNull String _INTERVAL = "I";
  public static final @NotNull String _LEFT = "left";
  public static final @NotNull String _RIGHT = "right";
  public static final @NotNull String _ARCOE = "arcoe";
  public static final @NotNull String _INVOL = "invol";

  private static final @NotNull Map<@NotNull String, @NotNull Supplier<PrimDef>> SUPPLIERS;

  static {
    Supplier<PrimDef> intervalSupplier = () -> new PrimDef(ImmutableSeq.empty(), ImmutableSeq.empty(),
      new FormTerm.Univ(new Sort(new Level.Constant<>(0), Sort.INF_LVL)), prim -> prim, _INTERVAL);
    Supplier<CallTerm.Prim> intervalCallSupplier = () -> new CallTerm.Prim(intervalSupplier.get().ref(),
      ImmutableSeq.empty(), ImmutableSeq.empty());

    SUPPLIERS = ImmutableMap.ofEntries(
      Tuple.of(_INTERVAL, intervalSupplier),
      Tuple.of(_LEFT,  () -> new PrimDef(ImmutableSeq.empty(), ImmutableSeq.empty(),
        intervalCallSupplier.get(), prim -> prim, _LEFT)),
      Tuple.of(_RIGHT , () -> new PrimDef(ImmutableSeq.empty(), ImmutableSeq.empty(),
        intervalCallSupplier.get(), prim -> prim, _RIGHT )),
      Tuple.of(_ARCOE, () -> {
        var paramA = new LocalVar("A");
        var paramIToATy = new Term.Param(new LocalVar(Constants.ANONYMOUS_PREFIX), intervalCallSupplier.get(), true);
        var paramI = new LocalVar("i");
        var homotopy = new Sort.LvlVar("h", LevelGenVar.Kind.Homotopy, null);
        var universe = new Sort.LvlVar("u", LevelGenVar.Kind.Universe, null);
        var result = new FormTerm.Univ(new Sort(new Level.Reference<>(universe), new Level.Reference<>(homotopy)));
        var paramATy = new FormTerm.Pi(paramIToATy, result);
        var aRef = new RefTerm(paramA, paramATy);
        var baseAtLeft = new ElimTerm.App(aRef, Arg.explicit(new CallTerm.Prim(LEFT.ref, ImmutableSeq.of(), ImmutableSeq.empty())));
        return new PrimDef(
          ImmutableSeq.of(
            new Term.Param(paramA, paramATy, true),
            new Term.Param(new LocalVar("base"), baseAtLeft, true),
            new Term.Param(paramI, intervalCallSupplier.get(), true)
          ),
          ImmutableSeq.of(homotopy, universe),
          new ElimTerm.App(aRef, Arg.explicit(new RefTerm(paramI, intervalCallSupplier.get()))),
          PrimDef::arcoe, "arcoe");
      }),
      Tuple.of(_INVOL, () -> {
        CallTerm.Prim intervalCall = new CallTerm.Prim(intervalSupplier.get().ref, ImmutableSeq.empty(), ImmutableSeq.empty());
        return new PrimDef(
          ImmutableSeq.of(new Term.Param(new LocalVar("i"), intervalCallSupplier.get(), true)), ImmutableSeq.of(),
          intervalCall, PrimDef::invol, _INVOL);
      })
    );
  }

  public static @NotNull Option<PrimDef> factory(@NotNull String name) {
    return SUPPLIERS.getOption(name).map(Supplier::get);
  }

  public boolean leftOrRight() {
    return ImmutableSeq.of("left", "right").contains(ref.name());
  }

  public boolean is(@NotNull String name) {
    return ref.name().equals(name);
  }

  public static final @NotNull Map<@NotNull String, @NotNull Option<PrimDef>> STATUS = ImmutableMap.ofEntries(
      Tuple.of(_INTERVAL, Option.none()),
      Tuple.of(_LEFT, Option.none()),
      Tuple.of(_RIGHT, Option.none()),
      Tuple.of(_ARCOE, Option.none()),
      Tuple.of(_INVOL, Option.none())
    );

  public @ApiStatus.Internal static void clearConcrete() {
    for (var var : PRIMITIVES.valuesView()) var.ref.concrete = null;
  }
}
