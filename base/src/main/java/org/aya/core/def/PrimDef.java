// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.def;

import kala.collection.Map;
import kala.collection.immutable.ImmutableSeq;
import kala.control.Option;
import kala.tuple.Tuple;
import kala.tuple.Tuple2;
import org.aya.concrete.stmt.Decl;
import org.aya.core.term.*;
import org.aya.generic.Arg;
import org.aya.generic.Constants;
import org.aya.generic.util.NormalizeMode;
import org.aya.ref.DefVar;
import org.aya.ref.LocalVar;
import org.aya.tyck.TyckState;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * @author ice1000
 */
public final class PrimDef extends TopLevelDef {
  public PrimDef(
    @NotNull DefVar<@NotNull PrimDef, Decl.@NotNull PrimDecl> ref,
    @NotNull ImmutableSeq<Term.Param> telescope,
    @NotNull Term result, @NotNull ID name
  ) {
    super(telescope, result);
    this.ref = ref;
    this.id = name;
    ref.core = this;
  }

  public PrimDef(@NotNull DefVar<@NotNull PrimDef, Decl.@NotNull PrimDecl> ref, @NotNull Term result, @NotNull ID name) {
    this(ref, ImmutableSeq.empty(), result, name);
  }

  public static @NotNull CallTerm.Prim intervalCall() {
    return new CallTerm.Prim(Factory.INSTANCE.getOption(ID.INTERVAL).get().ref(),
      0, ImmutableSeq.empty());
  }

  @Override public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
    return visitor.visitPrim(this, p);
  }

  public @NotNull Term unfold(@NotNull CallTerm.Prim primCall, @Nullable TyckState state) {
    return Factory.INSTANCE.unfold(Objects.requireNonNull(ID.find(ref.name())), primCall, state);
  }

  public @NotNull ImmutableSeq<Term.Param> telescope() {
    if (telescope.isEmpty()) return telescope;
    if (ref.concrete != null) {
      var signature = ref.concrete.signature;
      if (signature != null) return signature.param();
    }
    return telescope;
  }

  public @NotNull Term result() {
    if (ref.concrete != null) {
      var signature = ref.concrete.signature;
      if (signature != null) return signature.result();
    }
    return result;
  }

  record PrimSeed(
    @NotNull ID name,
    @NotNull BiFunction<CallTerm.@NotNull Prim, @Nullable TyckState, @NotNull Term> unfold,
    @NotNull Function<@NotNull DefVar<PrimDef, Decl.PrimDecl>, @NotNull PrimDef> supplier,
    @NotNull ImmutableSeq<@NotNull ID> dependency
  ) {
    public @NotNull PrimDef supply(@NotNull DefVar<PrimDef, Decl.PrimDecl> ref) {
      return supplier.apply(ref);
    }

    public static final @NotNull PrimSeed INTERVAL = new PrimSeed(
      ID.INTERVAL,
      (prim, state) -> prim,
      ref -> new PrimDef(ref, FormTerm.Univ.ZERO, ID.INTERVAL),
      ImmutableSeq.empty()
    );
    public static final @NotNull PrimDef.PrimSeed LEFT = new PrimSeed(
      ID.LEFT,
      (prim, state) -> prim,
      ref -> new PrimDef(ref, intervalCall(), ID.LEFT),
      ImmutableSeq.of(ID.INTERVAL)
    );

    // Right
    public static final @NotNull PrimDef.PrimSeed RIGHT = new PrimSeed(
      ID.RIGHT,
      (prim, state) -> prim,
      ref -> new PrimDef(ref, intervalCall(), ID.RIGHT),
      ImmutableSeq.of(ID.INTERVAL)
    );

    /** Arend's coe */
    private static @NotNull Term arcoe(CallTerm.@NotNull Prim prim, @Nullable TyckState state) {
      var args = prim.args();
      var argBase = args.get(1);
      var argI = args.get(2);
      var left = Factory.INSTANCE.getOption(ID.LEFT);
      if (argI.term() instanceof CallTerm.Prim primCall && left.isNotEmpty() && primCall.ref() == left.get().ref)
        return argBase.term();
      var argA = args.get(0).term();

      if (argA instanceof IntroTerm.Lambda lambda) {
        var normalize = lambda.body().normalize(state, NormalizeMode.NF);
        if (normalize.findUsages(lambda.param().ref()) == 0) return argBase.term();
        else return new CallTerm.Prim(prim.ref(), prim.ulift(), ImmutableSeq.of(
          new Arg<>(new IntroTerm.Lambda(lambda.param(), normalize), true), argBase, argI));
      }
      return prim;
    }

    public static final @NotNull PrimDef.PrimSeed ARCOE = new PrimSeed(ID.ARCOE, PrimSeed::arcoe, ref -> {
      var paramA = new LocalVar("A");
      var paramIToATy = new Term.Param(new LocalVar(Constants.ANONYMOUS_PREFIX), intervalCall(), true);
      var paramI = new LocalVar("i");
      var result = new FormTerm.Univ(0);
      var paramATy = new FormTerm.Pi(paramIToATy, result);
      var aRef = new RefTerm(paramA);
      var left = Factory.INSTANCE.getOption(ID.LEFT).get();
      var baseAtLeft = new ElimTerm.App(aRef, new Arg<>(new CallTerm.Prim(left.ref, 0, ImmutableSeq.empty()), true));
      return new PrimDef(
        ref,
        ImmutableSeq.of(
          new Term.Param(paramA, paramATy, true),
          new Term.Param(new LocalVar("base"), baseAtLeft, true),
          new Term.Param(paramI, intervalCall(), true)
        ),
        new ElimTerm.App(aRef, new Arg<>(new RefTerm(paramI), true)),
        ID.ARCOE
      );
    }, ImmutableSeq.of(ID.INTERVAL, ID.LEFT));

    private static @NotNull Tuple2<PrimDef, PrimDef> leftRight() {
      return Tuple.of(
        Factory.INSTANCE.getOption(ID.LEFT).get(),
        Factory.INSTANCE.getOption(ID.RIGHT).get());
    }

    /** Involution, ~ in Cubical Agda */
    private static @NotNull Term invol(CallTerm.@NotNull Prim prim, @Nullable TyckState state) {
      var arg = prim.args().get(0).term().normalize(state, NormalizeMode.WHNF);
      if (arg instanceof CallTerm.Prim primCall) {
        var lr = leftRight();
        var left = lr._1;
        var right = lr._2;
        if (primCall.ref() == left.ref)
          return new CallTerm.Prim(right.ref, 0, ImmutableSeq.empty());
        if (primCall.ref() == right.ref)
          return new CallTerm.Prim(left.ref, 0, ImmutableSeq.empty());
      }
      return new CallTerm.Prim(prim.ref(), 0, ImmutableSeq.of(new Arg<>(arg, true)));
    }

    public static final @NotNull PrimDef.PrimSeed INVOL = new PrimSeed(ID.INVOL, PrimSeed::invol, ref -> new PrimDef(
      ref,
      ImmutableSeq.of(new Term.Param(new LocalVar("i"), intervalCall(), true)),
      intervalCall(),
      ID.INVOL
    ), ImmutableSeq.of(ID.INTERVAL));

    /** <code>/\</code> in CCHM, <code>I.squeeze</code> in Arend */
    private static @NotNull Term squeezeLeft(CallTerm.@NotNull Prim prim, @Nullable TyckState state) {
      var lhsArg = prim.args().get(0).term().normalize(state, NormalizeMode.WHNF);
      var rhsArg = prim.args().get(1).term().normalize(state, NormalizeMode.WHNF);
      var lr = leftRight();
      var left = lr._1;
      var right = lr._2;
      if (lhsArg instanceof CallTerm.Prim lhs) {
        if (lhs.ref() == left.ref) return lhs;
        if (lhs.ref() == right.ref) return rhsArg;
      } else if (rhsArg instanceof CallTerm.Prim rhs) {
        if (rhs.ref() == left.ref) return rhs;
        if (rhs.ref() == right.ref) return lhsArg;
      }
      return prim;
    }


    public static final @NotNull PrimDef.PrimSeed SQUEEZE_LEFT =
      new PrimSeed(ID.SQUEEZE_LEFT, PrimSeed::squeezeLeft, ref -> new PrimDef(
        ref,
        ImmutableSeq.of(
          new Term.Param(new LocalVar("i"), intervalCall(), true),
          new Term.Param(new LocalVar("j"), intervalCall(), true)),
        intervalCall(),
        ID.SQUEEZE_LEFT
      ), ImmutableSeq.of(ID.INTERVAL));
  }

  public static class Factory {
    private final @NotNull EnumMap<@NotNull ID, @NotNull PrimDef> defs = new EnumMap<>(ID.class);
    public static final @NotNull PrimDef.Factory INSTANCE = new Factory();

    private Factory() {
    }

    private static final @NotNull Map<@NotNull ID, @NotNull PrimSeed> SEEDS = ImmutableSeq.of(
        PrimSeed.INTERVAL,
        PrimSeed.LEFT,
        PrimSeed.RIGHT,
        PrimSeed.ARCOE,
        PrimSeed.SQUEEZE_LEFT,
        PrimSeed.INVOL
      ).map(seed -> Tuple.of(seed.name, seed))
      .toImmutableMap();

    public @NotNull PrimDef factory(@NotNull ID name, @NotNull DefVar<PrimDef, Decl.PrimDecl> ref) {
      assert !have(name);
      var rst = SEEDS.get(name).supply(ref);
      defs.put(name, rst);
      return rst;
    }

    public @NotNull Option<PrimDef> getOption(@NotNull ID name) {
      return Option.of(defs.get(name));
    }

    public boolean have(@NotNull ID name) {
      return defs.containsKey(name);
    }

    public @NotNull PrimDef getOrCreate(@NotNull ID name, @NotNull DefVar<PrimDef, Decl.PrimDecl> ref) {
      return getOption(name).getOrElse(() -> factory(name, ref));
    }

    public @NotNull Option<ImmutableSeq<@NotNull ID>> checkDependency(@NotNull ID name) {
      return SEEDS.getOption(name).map(seed -> seed.dependency().filterNot(this::have));
    }

    public @NotNull Term unfold(@NotNull ID name, @NotNull CallTerm.Prim primCall, @Nullable TyckState state) {
      return SEEDS.get(name).unfold.apply(primCall, state);
    }

    public static final @NotNull ImmutableSeq<ID> LEFT_RIGHT = ImmutableSeq.of(ID.LEFT, ID.RIGHT);

    public boolean leftOrRight(PrimDef core) {
      for (var primName : LEFT_RIGHT) {
        var cur = getOption(primName);
        if (cur.isNotEmpty() && core == cur.get())
          return true;
      }
      return false;
    }

    public void clear() {
      defs.clear();
    }
  }

  public enum ID {
    INTERVAL("I"), LEFT("left"), RIGHT("right"),
    /** Short for <em>Arend coe</em>. */
    ARCOE("arcoe"),
    SQUEEZE_LEFT("squeezeL"),
    INVOL("invol");
    public final @NotNull @NonNls String id;

    @Override public String toString() {
      return id;
    }

    public static @Nullable ID find(@NotNull String id) {
      for (var value : PrimDef.ID.values())
        if (Objects.equals(value.id, id)) return value;
      return null;
    }

    ID(@NotNull String id) {
      this.id = id;
    }
  }

  public final @NotNull DefVar<@NotNull PrimDef, Decl.PrimDecl> ref;
  public final @NotNull ID id;

  public @NotNull DefVar<@NotNull PrimDef, Decl.PrimDecl> ref() {
    return ref;
  }
}
