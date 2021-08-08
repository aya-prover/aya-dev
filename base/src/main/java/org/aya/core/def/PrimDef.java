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
import org.jetbrains.annotations.NotNull;
import java.util.function.Function;

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

  private static @NotNull Function<CallTerm.@NotNull Prim, @NotNull Term> arcoe(@NotNull MutableMap<@NotNull String, @NotNull PrimDef> status) {
    return (prim) -> {
      var args = prim.args();
      var argBase = args.get(1).term();
      var argI = args.get(2).term();
      if (argI instanceof CallTerm.Prim primCall && status.containsKey(_LEFT) && primCall.ref() == status.get(_LEFT).ref)
        return argBase;
      var argA = args.get(0).term();
      if (argA instanceof IntroTerm.Lambda lambda && lambda.body().findUsages(lambda.param().ref()) == 0) return argBase;
      return prim;
    };
  }

  private static @NotNull Function<CallTerm.@NotNull Prim, @NotNull Term> invol(@NotNull MutableMap<@NotNull String, @NotNull PrimDef> status) {
    return (prim) -> {
      var arg = prim.args().get(0).term();
      if (arg instanceof CallTerm.Prim primCall) {
        if (status.containsKey(_LEFT) && primCall.ref() == status.get(_LEFT).ref)
          return new CallTerm.Prim(status.get(_LEFT).ref, ImmutableSeq.empty(), ImmutableSeq.empty());
        if (status.containsKey(_RIGHT) && primCall.ref() == status.get(_RIGHT).ref)
          return new CallTerm.Prim(status.get(_RIGHT).ref, ImmutableSeq.empty(), ImmutableSeq.empty());
      }
      return prim;
    };
  }

  @Override public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
    return visitor.visitPrim(this, p);
  }

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

  public static final @NotNull String _INTERVAL = "I";
  public static final @NotNull String _LEFT = "left";
  public static final @NotNull String _RIGHT = "right";
  /** Short for <em>Arend coe</em>. */
  public static final @NotNull String _ARCOE = "arcoe";
  public static final @NotNull String _INVOL = "invol";

  public static final @NotNull ImmutableSeq<String> LEFT_RIGHT = ImmutableSeq.of(_LEFT, _RIGHT);

  private static final @NotNull Map<@NotNull String, @NotNull Function<@NotNull MutableMap<@NotNull String, @NotNull PrimDef> , @NotNull PrimDef>> SUPPLIERS;

  static {
    Function<@NotNull MutableMap<@NotNull String, @NotNull PrimDef> , @NotNull PrimDef> intervalSupplier =
      (status) -> new PrimDef(ImmutableSeq.empty(), ImmutableSeq.empty(),
      new FormTerm.Univ(new Sort(new Level.Constant<>(0), Sort.INF_LVL)), prim -> prim, _INTERVAL);
    Function<@NotNull MutableMap<@NotNull String, @NotNull PrimDef> , CallTerm.Prim> intervalCallSupplier =
      (status) -> new CallTerm.Prim(intervalSupplier.apply(status).ref(),
      ImmutableSeq.empty(), ImmutableSeq.empty());

    Function<@NotNull MutableMap<@NotNull String, @NotNull PrimDef> , @NotNull PrimDef> leftSupplier =
      (status) -> new PrimDef(ImmutableSeq.empty(),
        ImmutableSeq.empty(), intervalCallSupplier.apply(status), prim -> prim, _LEFT);

    SUPPLIERS = ImmutableMap.ofEntries(
      Tuple.of(_INTERVAL, intervalSupplier),
      Tuple.of(_LEFT,  (status) -> new PrimDef(ImmutableSeq.empty(), ImmutableSeq.empty(),
        intervalCallSupplier.apply(status), prim -> prim, _LEFT)),
      Tuple.of(_RIGHT , (status) -> new PrimDef(ImmutableSeq.empty(), ImmutableSeq.empty(),
        intervalCallSupplier.apply(status), prim -> prim, _RIGHT )),
      Tuple.of(_ARCOE, (status) -> {
        var paramA = new LocalVar("A");
        var paramIToATy = new Term.Param(new LocalVar(Constants.ANONYMOUS_PREFIX), intervalCallSupplier.apply(status), true);
        var paramI = new LocalVar("i");
        var homotopy = new Sort.LvlVar("h", LevelGenVar.Kind.Homotopy, null);
        var universe = new Sort.LvlVar("u", LevelGenVar.Kind.Universe, null);
        var result = new FormTerm.Univ(new Sort(new Level.Reference<>(universe), new Level.Reference<>(homotopy)));
        var paramATy = new FormTerm.Pi(paramIToATy, result);
        var aRef = new RefTerm(paramA, paramATy);
        var left = status.getOption(_LEFT).getOrElse(
          () -> PrimDef.factory(_LEFT, status).get()
        );
        var baseAtLeft = new ElimTerm.App(aRef, Arg.explicit(
          new CallTerm.Prim(left.ref, ImmutableSeq.of(), ImmutableSeq.empty())));
        return new PrimDef(
          ImmutableSeq.of(
            new Term.Param(paramA, paramATy, true),
            new Term.Param(new LocalVar("base"), baseAtLeft, true),
            new Term.Param(paramI, intervalCallSupplier.apply(status), true)
          ),
          ImmutableSeq.of(homotopy, universe),
          new ElimTerm.App(aRef, Arg.explicit(new RefTerm(paramI, intervalCallSupplier.apply(status)))),
          PrimDef.arcoe(status), "arcoe");
      }),
      Tuple.of(_INVOL, (status) -> {
        CallTerm.Prim intervalCall = new CallTerm.Prim(intervalSupplier.apply(status).ref, ImmutableSeq.empty(), ImmutableSeq.empty());
        return new PrimDef(
          ImmutableSeq.of(new Term.Param(new LocalVar("i"), intervalCallSupplier.apply(status), true)), ImmutableSeq.of(),
          intervalCall, PrimDef.invol(status), _INVOL);
      })
    );
  }

  public static @NotNull Option<PrimDef> factory(
    @NotNull String name,
    @NotNull MutableMap<@NotNull String, @NotNull PrimDef> status
  ) {
    if (status.containsKey(name)) {
      return Option.none();
    }
    var rst = SUPPLIERS.getOption(name).map(
      (f) -> f.apply(status)
    );
    if (rst.isNotEmpty()) {
      status.set(name, rst.get());
    }
    return rst;
  }

  public boolean leftOrRight() {
    return ImmutableSeq.of("left", "right").contains(ref.name());
  }

  public boolean is(@NotNull String name) {
    return ref.name().equals(name);
  }
}
