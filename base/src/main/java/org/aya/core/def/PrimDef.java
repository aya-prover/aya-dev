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
import org.jetbrains.annotations.Contract;
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
public final class PrimDef extends TopLevelDef<Term> {
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
  interface Unfolder extends BiFunction<@NotNull PrimCall, @NotNull TyckState, @NotNull Term> {
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
      public final @NotNull PrimDef.PrimSeed coe = new PrimSeed(ID.COE, this::coe, ref -> {
        var varA = new LocalVar("A");
        var paramA = new Term.Param(varA, intervalToA(), true);
        var paramRestr = new Term.Param(new LocalVar("i"), IntervalTerm.INSTANCE, true);
        var result = familyLeftToRight(new RefTerm(varA));

        return new PrimDef(ref, ImmutableSeq.of(paramA, paramRestr), result, ID.COE);
      }, ImmutableSeq.of(ID.I));

      @Contract("_, _ -> new")
      private @NotNull Term coe(@NotNull PrimCall prim, @NotNull TyckState state) {
        var type = prim.args().get(0).term();
        var restr = prim.args().get(1).term();
        return new CoeTerm(type, isOne(restr));
      }

      private final @NotNull PrimDef.PrimSeed hcomp = new PrimSeed(ID.HCOMP, this::hcomp, ref -> {
        var varA = new LocalVar("A");
        var paramA = new Term.Param(varA, SortTerm.Type0, false);
        var varPhi = new LocalVar("phi");
        var paramRestr = new Term.Param(varPhi, IntervalTerm.INSTANCE, false);
        var varU = new LocalVar("u");
        var paramFuncU = new Term.Param(varU,
          new PiTerm(
            new Term.Param(LocalVar.IGNORED, IntervalTerm.INSTANCE, true),
            new PartialTyTerm(new RefTerm(varA), isOne(new RefTerm(varPhi)))),
          true);
        var varU0 = new LocalVar("u0");
        var paramU0 = new Term.Param(varU0, new RefTerm(varA), true);
        var result = new RefTerm(varA);
        return new PrimDef(
          ref,
          ImmutableSeq.of(paramA, paramRestr, paramFuncU, paramU0),
          result,
          ID.HCOMP
        );
      }, ImmutableSeq.of(ID.I));

      private @NotNull Term hcomp(@NotNull PrimCall prim, @NotNull TyckState state) {
        var A = prim.args().get(0).term();
        var phi = prim.args().get(1).term();
        var u = prim.args().get(2).term();
        var u0 = prim.args().get(3).term();
        return new HCompTerm(A, phi, u, u0);
      }

      /** /\ in Cubical Agda, should elaborate to {@link Formula.Conn} */
      public final @NotNull PrimDef.PrimSeed intervalMin = formula(ID.IMIN, prim -> {
        var args = prim.args();
        return FormulaTerm.and(args.first().term(), args.last().term());
      }, "i", "j");
      /** \/ in Cubical Agda, should elaborate to {@link Formula.Conn} */
      public final @NotNull PrimDef.PrimSeed intervalMax = formula(ID.IMAX, prim -> {
        var args = prim.args();
        return FormulaTerm.or(args.first().term(), args.last().term());
      }, "i", "j");
      /** ~ in Cubical Agda, should elaborate to {@link Formula.Inv} */
      public final @NotNull PrimDef.PrimSeed intervalInv = formula(ID.INVOL, prim ->
        FormulaTerm.inv(prim.args().first().term()), "i");

      private @NotNull PrimSeed formula(
        ID id, Function<PrimCall, Term> unfold,
        String... tele
      ) {
        return new PrimSeed(id, (prim, state) -> unfold.apply(prim), ref -> new PrimDef(
          ref,
          ImmutableSeq.of(tele).map(n -> new Term.Param(new LocalVar(n), IntervalTerm.INSTANCE, true)),
          IntervalTerm.INSTANCE,
          id
        ), ImmutableSeq.of(ID.I));
      }

      public final @NotNull PrimDef.PrimSeed stringType =
        new PrimSeed(ID.STRING,
          ((prim, tyckState) -> prim),
          ref -> new PrimDef(ref, SortTerm.Type0, ID.STRING), ImmutableSeq.empty());
      public final @NotNull PrimDef.PrimSeed stringConcat =
        new PrimSeed(ID.STRCONCAT, Initializer::concat, ref -> new PrimDef(
          ref,
          ImmutableSeq.of(
            new Term.Param(new LocalVar("str1"), getCall(ID.STRING, ImmutableSeq.empty()), true),
            new Term.Param(new LocalVar("str2"), getCall(ID.STRING, ImmutableSeq.empty()), true)
          ),
          getCall(ID.STRING, ImmutableSeq.empty()),
          ID.STRCONCAT
        ), ImmutableSeq.of(ID.STRING));

      private static @NotNull Term concat(@NotNull PrimCall prim, @NotNull TyckState state) {
        var first = prim.args().get(0).term().normalize(state, NormalizeMode.WHNF);
        var second = prim.args().get(1).term().normalize(state, NormalizeMode.WHNF);

        if (first instanceof StringTerm str1 && second instanceof StringTerm str2) {
          return new StringTerm(str1.string() + str2.string());
        }

        return new PrimCall(prim.ref(), prim.ulift(), ImmutableSeq.of(
          new Arg<>(first, true), new Arg<>(second, true)));
      }

      public final @NotNull PrimDef.PrimSeed intervalType =
        new PrimSeed(ID.I,
          ((prim, state) -> IntervalTerm.INSTANCE),
          ref -> new PrimDef(ref, SortTerm.ISet, ID.I),
          ImmutableSeq.empty());

      public final @NotNull PrimDef.PrimSeed partialType =
        new PrimSeed(ID.PARTIAL,
          (prim, state) -> {
            var iExp = prim.args().get(0).term();
            var ty = prim.args().get(1).term();

            return new PartialTyTerm(ty, isOne(iExp));
          },
          ref -> new PrimDef(
            ref,
            ImmutableSeq.of(
              new Term.Param(new LocalVar("phi"), IntervalTerm.INSTANCE, true),
              new Term.Param(new LocalVar("A"), SortTerm.Type0, true)
            ),
            SortTerm.Type0, ID.PARTIAL),
          ImmutableSeq.of(ID.I));
    }

    private final @NotNull EnumMap<@NotNull ID, @NotNull PrimDef> defs = new EnumMap<>(ID.class);

    private final @NotNull Map<@NotNull ID, @NotNull PrimSeed> seeds;

    public Factory() {
      var init = new Initializer();
      seeds = ImmutableSeq.of(
          init.intervalMin,
          init.intervalMax,
          init.intervalInv,
          init.stringType,
          init.stringConcat,
          init.intervalType,
          init.partialType,
          init.coe,
          init.hcomp
        ).map(seed -> Tuple.of(seed.name, seed))
        .toImmutableMap();
    }

    public @NotNull PrimDef factory(@NotNull ID name, @NotNull DefVar<PrimDef, TeleDecl.PrimDecl> ref) {
      assert !have(name);
      var rst = seeds.get(name).supply(ref);
      defs.put(name, rst);
      return rst;
    }

    public @NotNull PrimCall getCall(@NotNull ID id, @NotNull ImmutableSeq<Arg<Term>> args) {
      return new PrimCall(getOption(id).get().ref(), 0, args);
    }

    public @NotNull PrimCall getCall(@NotNull ID id) {
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
      return seeds.getOption(name).map(seed -> seed.dependency().filterNot(this::have));
    }

    public @NotNull Term unfold(@NotNull ID name, @NotNull PrimCall primCall, @NotNull TyckState state) {
      return seeds.get(name).unfold.apply(primCall, state);
    }

    public void clear() {
      defs.clear();
    }

    public void clear(@NotNull ID name) {
      defs.remove(name);
    }
  }

  /** Let A be argument, then <code>A 0 -> A 1</code> */
  public static @NotNull PiTerm familyLeftToRight(Term term) {
    return new PiTerm(
      new Term.Param(LocalVar.IGNORED, new AppTerm(term, new Arg<>(FormulaTerm.LEFT, true)), true),
      new AppTerm(term, new Arg<>(FormulaTerm.RIGHT, true)));
  }

  public static @NotNull Term intervalToA() {
    var paramI = new Term.Param(LocalVar.IGNORED, IntervalTerm.INSTANCE, true);
    return new PiTerm(paramI, SortTerm.Type0);
  }

  public enum ID {
    IMIN("intervalMin"),
    IMAX("intervalMax"),
    INVOL("intervalInv"),
    STRING("String"),
    STRCONCAT("strcat"),
    I("I"),
    PARTIAL("Partial"),
    COE("coe"),
    HCOMP("hcomp");

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
