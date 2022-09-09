// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.def;

import kala.collection.Map;
import kala.collection.immutable.ImmutableSeq;
import kala.control.Option;
import kala.tuple.Tuple;
import org.aya.concrete.stmt.TeleDecl;
import org.aya.core.term.*;
import org.aya.generic.Arg;
import org.aya.generic.util.NormalizeMode;
import org.aya.guest0x0.cubical.Formula;
import org.aya.ref.DefVar;
import org.aya.ref.LocalVar;
import org.aya.tyck.TyckState;
import org.aya.util.ForLSP;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

import static org.aya.guest0x0.cubical.CofThy.isOne;

/**
 * @author ice1000
 */
public final class PrimDef extends TopLevelDef {
  public PrimDef(
    @NotNull DefVar<@NotNull PrimDef, TeleDecl.@NotNull PrimDecl> ref,
    @NotNull ImmutableSeq<Term.Param> telescope,
    @NotNull Term result, @NotNull ID name
  ) {
    super(telescope, result);
    this.ref = ref;
    this.id = name;
    ref.core = this;
  }

  public PrimDef(@NotNull DefVar<@NotNull PrimDef, TeleDecl.@NotNull PrimDecl> ref, @NotNull Term result, @NotNull ID name) {
    this(ref, ImmutableSeq.empty(), result, name);
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

  @FunctionalInterface
  interface Unfolder extends BiFunction<CallTerm.@NotNull Prim, @NotNull TyckState, @NotNull Term> {
  }

  record PrimSeed(
    @NotNull ID name,
    @NotNull Unfolder unfold,
    @NotNull Function<@NotNull DefVar<PrimDef, TeleDecl.PrimDecl>, @NotNull PrimDef> supplier,
    @NotNull ImmutableSeq<@NotNull ID> dependency
  ) {
    public @NotNull PrimDef supply(@NotNull DefVar<PrimDef, TeleDecl.PrimDecl> ref) {
      return supplier.apply(ref);
    }
  }

  public static class Factory {
    private final class Initializer {


      /** Arend's coe */
      private @NotNull Term arcoe(CallTerm.@NotNull Prim prim, @NotNull TyckState state) {
        var args = prim.args();
        var argBase = args.get(1);
        var argI = args.get(2);
        if (argI.term().asFormula() instanceof Formula.Lit<Term> end && end.isLeft())
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

      public final @NotNull PrimDef.PrimSeed ARCOE = new PrimSeed(ID.ARCOE, this::arcoe, ref -> {
        var paramA = new LocalVar("A");
        var paramIToATy = new Term.Param(LocalVar.IGNORED, PrimTerm.Interval.INSTANCE, true);
        var paramI = new LocalVar("i");
        var result = new FormTerm.Type(0);
        var paramATy = new FormTerm.Pi(paramIToATy, result);
        var aRef = new RefTerm(paramA);
        var baseAtLeft = new ElimTerm.App(aRef, new Arg<>(PrimTerm.Mula.LEFT, true));
        return new PrimDef(
          ref,
          ImmutableSeq.of(
            new Term.Param(paramA, paramATy, true),
            new Term.Param(new LocalVar("base"), baseAtLeft, true),
            new Term.Param(paramI, PrimTerm.Interval.INSTANCE, true)
          ),
          new ElimTerm.App(aRef, new Arg<>(new RefTerm(paramI), true)),
          ID.ARCOE
        );
      }, ImmutableSeq.of(ID.I));

      /** /\ in Cubical Agda, should elaborate to {@link Formula.Conn} */
      public final @NotNull PrimDef.PrimSeed IMIN = formula(ID.IMIN, prim -> {
        var args = prim.args();
        return PrimTerm.Mula.and(args.first().term(), args.last().term());
      }, "i", "j");
      /** \/ in Cubical Agda, should elaborate to {@link Formula.Conn} */
      public final @NotNull PrimDef.PrimSeed IMAX = formula(ID.IMAX, prim -> {
        var args = prim.args();
        return PrimTerm.Mula.or(args.first().term(), args.last().term());
      }, "i", "j");
      /** ~ in Cubical Agda, should elaborate to {@link Formula.Inv} */
      public final @NotNull PrimDef.PrimSeed INVOL = formula(ID.INVOL, prim ->
          PrimTerm.Mula.inv(prim.args().first().term()), "i");

      private @NotNull PrimSeed formula(
        ID id, Function<CallTerm.Prim, Term> unfold,
        String... tele
      ) {
        return new PrimSeed(id, (prim, state) -> unfold.apply(prim), ref -> new PrimDef(
          ref,
          ImmutableSeq.of(tele).map(n -> new Term.Param(new LocalVar(n), PrimTerm.Interval.INSTANCE, true)),
          PrimTerm.Interval.INSTANCE,
          id
        ), ImmutableSeq.of(ID.I));
      }

      public final @NotNull PrimDef.PrimSeed STR =
        new PrimSeed(ID.STR,
          ((prim, tyckState) -> prim),
          ref -> new PrimDef(ref, FormTerm.Type.ZERO, ID.STR), ImmutableSeq.empty());
      public final @NotNull PrimDef.PrimSeed STRCONCAT =
        new PrimSeed(ID.STRCONCAT, Initializer::concat, ref -> new PrimDef(
          ref,
          ImmutableSeq.of(
            new Term.Param(new LocalVar("str1"), getCall(ID.STR, ImmutableSeq.empty()), true),
            new Term.Param(new LocalVar("str2"), getCall(ID.STR, ImmutableSeq.empty()), true)
          ),
          getCall(ID.STR, ImmutableSeq.empty()),
          ID.STRCONCAT
        ), ImmutableSeq.of(ID.STR));

      private static @NotNull Term concat(CallTerm.@NotNull Prim prim, @NotNull TyckState state) {
        var first = prim.args().get(0).term().normalize(state, NormalizeMode.WHNF);
        var second = prim.args().get(1).term().normalize(state, NormalizeMode.WHNF);

        if (first instanceof PrimTerm.Str str1 && second instanceof PrimTerm.Str str2) {
          return new PrimTerm.Str(str1.string() + str2.string());
        }

        return new CallTerm.Prim(prim.ref(), prim.ulift(), ImmutableSeq.of(
          new Arg<>(first, true), new Arg<>(second, true)));
      }

      public final @NotNull PrimDef.PrimSeed I =
        new PrimSeed(ID.I,
          ((prim, state) -> PrimTerm.Interval.INSTANCE),
          ref -> new PrimDef(ref, FormTerm.Type.ZERO, ID.I),
          ImmutableSeq.empty());

      public final @NotNull PrimDef.PrimSeed PARTIAL =
        new PrimSeed(ID.PARTIAL,
          (prim, state) -> {
            var iExp = prim.args().get(0).term();
            var ty = prim.args().get(1).term();

            return new FormTerm.PartTy(ty, isOne(iExp));
          },
          ref -> new PrimDef(
            ref,
            ImmutableSeq.of(
              new Term.Param(new LocalVar("phi"), PrimTerm.Interval.INSTANCE, true),
              new Term.Param(new LocalVar("A"), FormTerm.Type.ZERO, true)
            ),
            FormTerm.Type.ZERO, ID.PARTIAL),
          ImmutableSeq.of(ID.I));
    }

    private final @NotNull EnumMap<@NotNull ID, @NotNull PrimDef> defs = new EnumMap<>(ID.class);

    private final @NotNull Map<@NotNull ID, @NotNull PrimSeed> SEEDS;

    public Factory() {
      var init = new Initializer();
      SEEDS = ImmutableSeq.of(
          init.ARCOE,
          init.IMIN,
          init.IMAX,
          init.INVOL,
          init.STR,
          init.STRCONCAT,
          init.I,
          init.PARTIAL
        ).map(seed -> Tuple.of(seed.name, seed))
        .toImmutableMap();
    }

    public @NotNull PrimDef factory(@NotNull ID name, @NotNull DefVar<PrimDef, TeleDecl.PrimDecl> ref) {
      assert !have(name);
      var rst = SEEDS.get(name).supply(ref);
      defs.put(name, rst);
      return rst;
    }

    public @NotNull CallTerm.Prim getCall(@NotNull ID id, @NotNull ImmutableSeq<Arg<Term>> args) {
      return new CallTerm.Prim(getOption(id).get().ref(), 0, args);
    }

    public @NotNull CallTerm.Prim getCall(@NotNull ID id) {
      return getCall(id, ImmutableSeq.empty());
    }

    public @NotNull Option<PrimDef> getOption(@NotNull ID name) {
      return Option.ofNullable(defs.get(name));
    }

    public boolean have(@NotNull ID name) {
      return defs.containsKey(name);
    }

    /** whether redefinition should be treated as error */
    @ForLSP public boolean suppressRedefinition() {
      return false;
    }

    public @NotNull PrimDef getOrCreate(@NotNull ID name, @NotNull DefVar<PrimDef, TeleDecl.PrimDecl> ref) {
      return getOption(name).getOrElse(() -> factory(name, ref));
    }

    public @NotNull Option<ImmutableSeq<@NotNull ID>> checkDependency(@NotNull ID name) {
      return SEEDS.getOption(name).map(seed -> seed.dependency().filterNot(this::have));
    }

    public @NotNull Term unfold(@NotNull ID name, @NotNull CallTerm.Prim primCall, @NotNull TyckState state) {
      return SEEDS.get(name).unfold.apply(primCall, state);
    }

    public void clear() {
      defs.clear();
    }

    public void clear(@NotNull ID name) {
      defs.remove(name);
    }
  }

  public enum ID {
    /** Short for <em>Arend coe</em>. */
    ARCOE("arcoe"),
    IMIN("intervalMin"),
    IMAX("intervalMax"),
    INVOL("invol"),
    STR("String"),
    STRCONCAT("strcat"),
    I("I"),
    PARTIAL("Partial");

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

  public final @NotNull DefVar<@NotNull PrimDef, TeleDecl.PrimDecl> ref;
  public final @NotNull ID id;

  public @NotNull DefVar<@NotNull PrimDef, TeleDecl.PrimDecl> ref() {
    return ref;
  }
}
