// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.def;

import kala.collection.Map;
import kala.collection.immutable.ImmutableSeq;
import kala.control.Option;
import kala.function.TriFunction;
import kala.tuple.Tuple;
import org.aya.concrete.stmt.TeleDecl;
import org.aya.core.term.*;
import org.aya.core.visitor.AyaRestrSimplifier;
import org.aya.generic.util.NormalizeMode;
import org.aya.guest0x0.cubical.Formula;
import org.aya.guest0x0.cubical.Partial;
import org.aya.guest0x0.cubical.Restr;
import org.aya.ref.DefVar;
import org.aya.ref.LocalVar;
import org.aya.tyck.TyckState;
import org.aya.util.Arg;
import org.aya.util.ForLSP;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

import static org.aya.core.term.SortTerm.Set0;
import static org.aya.core.term.SortTerm.Type0;

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

  /** <code>I -> Type</code> */
  public static @NotNull Term intervalToType() {
    var paramI = new Term.Param(LocalVar.IGNORED, IntervalTerm.INSTANCE, true);
    return new PiTerm(paramI, Type0);
  }

  /** Let A be argument, then <code>A 0 -> A 1</code> */
  public static @NotNull PiTerm familyLeftToRight(Term term) {
    return familyI2J(term, FormulaTerm.LEFT, FormulaTerm.RIGHT);
  }

  /** Let A be argument, then <code>A 1 -> A 0</code> */
  public static @NotNull PiTerm familyRightToLeft(Term term) {
    return familyI2J(term, FormulaTerm.RIGHT, FormulaTerm.LEFT);
  }

  /** Let A be argument, then <code>A i -> A j</code> */
  private static @NotNull PiTerm familyI2J(Term term, FormulaTerm i, FormulaTerm j) {
    return new PiTerm(
      new Term.Param(LocalVar.IGNORED, new AppTerm(term, new Arg<>(i, true)), true),
      new AppTerm(term, new Arg<>(j, true)));
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
    COEFILL("coeFill"),
    COEINV("eoc"),
    COEINVFILL("eocFill"),
    HCOMP("hcomp"),
    SUB("Sub"),
    INS("inS"),
    OUTS("outS");

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

    public static boolean projSyntax(@NotNull ID id) {
      return id == COE || id == COEFILL || id == COEINV || id == COEINVFILL;
    }
  }

  public static class Factory {
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
          init.coeFill,
          init.eoc,
          init.eocFill,
          init.hcomp,
          init.outS,
          init.inS,
          init.sub
        ).map(seed -> Tuple.of(seed.name, seed))
        .toImmutableMap();
    }

    private final @NotNull EnumMap<@NotNull ID, @NotNull PrimDef> defs = new EnumMap<>(ID.class);

    private final @NotNull Map<@NotNull ID, @NotNull PrimSeed> seeds;

    private final class Initializer {
      public final @NotNull PrimDef.PrimSeed coe = new PrimSeed(ID.COE, this::coe, ref -> {
        // coe (A : I -> Type) (phi : I) : A 0 -> A 1
        var varA = new LocalVar("A");
        var paramA = new Term.Param(varA, intervalToType(), true);
        var paramRestr = new Term.Param(new LocalVar("i"), IntervalTerm.INSTANCE, true);
        var result = familyLeftToRight(new RefTerm(varA));

        return new PrimDef(ref, ImmutableSeq.of(paramA, paramRestr), result, ID.COE);
      }, ImmutableSeq.of(ID.I));

      public final @NotNull PrimDef.PrimSeed sub = new PrimSeed(ID.SUB, this::sub, ref -> {
        // Sub (A: Type) (φ: I) (u: Partial φ A) : Set
        var varA = new LocalVar("A");
        var varPhi = new LocalVar("phi");
        var varU = new LocalVar("u");
        var paramA = new Term.Param(varA, Type0, true);
        var paramPhi = new Term.Param(varPhi, IntervalTerm.INSTANCE, true);
        var paramU = new Term.Param(varU, new PartialTyTerm(new RefTerm(varA), AyaRestrSimplifier.INSTANCE.isOne(new RefTerm(varPhi))), true);
        return new PrimDef(ref, ImmutableSeq.of(paramA, paramPhi, paramU), Set0, ID.SUB);
      }, ImmutableSeq.of(ID.I, ID.PARTIAL));

      public final @NotNull PrimDef.PrimSeed outS = new PrimSeed(ID.OUTS, this::insideOut, ref -> {
        // outS {A} {φ} {u : Partial φ A} (Sub A φ u) : A
        var varA = new LocalVar("A");
        var varPhi = new LocalVar("phi");
        var varU = new LocalVar("u");
        var paramA = new Term.Param(varA, Type0, false);
        var paramPhi = new Term.Param(varPhi, IntervalTerm.INSTANCE, false);
        var phi = new RefTerm(varPhi);
        var A = new RefTerm(varA);
        var paramU = new Term.Param(varU, new PartialTyTerm(A, AyaRestrSimplifier.INSTANCE.isOne(phi)), false);

        var paramSub = new Term.Param(LocalVar.IGNORED,
          getCall(ID.SUB, ImmutableSeq.of(new Arg<>(A, true),
            new Arg<>(phi, true), new Arg<>(new RefTerm(varU), true))), true);
        return new PrimDef(ref, ImmutableSeq.of(paramA, paramPhi, paramU, paramSub), A, ID.OUTS);
      }, ImmutableSeq.of(ID.PARTIAL, ID.SUB));

      public final @NotNull PrimDef.PrimSeed inS = new PrimSeed(ID.INS, this::insideOut, ref -> {
        // outS {A} {φ} (u : A) : Sub A φ {|φ ↦ u|}
        var varA = new LocalVar("A");
        var varPhi = new LocalVar("phi");
        var varU = new LocalVar("u");
        var paramA = new Term.Param(varA, Type0, false);
        var paramPhi = new Term.Param(varPhi, IntervalTerm.INSTANCE, false);
        var phi = new RefTerm(varPhi);
        var A = new RefTerm(varA);
        var paramU = new Term.Param(varU, A, true);
        var u = new RefTerm(varU);

        var par = new PartialTerm(PartialTerm.from(phi, u), A);
        var ret = getCall(ID.SUB, ImmutableSeq.of(new Arg<>(A, true),
          new Arg<>(phi, true), new Arg<>(par, true)));
        return new PrimDef(ref, ImmutableSeq.of(paramA, paramPhi, paramU), ret, ID.INS);
      }, ImmutableSeq.of(ID.PARTIAL, ID.SUB));

      public final @NotNull PrimDef.PrimSeed stringType =
        new PrimSeed(ID.STRING,
          ((prim, tyckState) -> prim),
          ref -> new PrimDef(ref, Type0, ID.STRING), ImmutableSeq.empty());

      @Contract("_, _ -> new")
      private @NotNull Term coe(@NotNull PrimCall prim, @NotNull TyckState state) {
        var type = prim.args().get(0).term();
        var restr = prim.args().get(1).term();
        return new CoeTerm(type, AyaRestrSimplifier.INSTANCE.isOne(restr));
      }

      public final @NotNull PrimDef.PrimSeed coeFill = new PrimSeed(ID.COEFILL, this::coeFill, ref -> {
        // coeFill (A : I -> Type) (phi : I) : Pi (u : A 0) -> Path A u (coe A phi u)
        return coeFillFactory(ref, ID.COEFILL, ID.COE, FormulaTerm.LEFT);
      }, ImmutableSeq.of(ID.I, ID.COE));

      private @NotNull Term coeFill(@NotNull PrimCall prim, @NotNull TyckState state) {
        return coeFillHelper(prim, CoeTerm::coeFill, FormulaTerm.LEFT);
      }

      public final @NotNull PrimDef.PrimSeed eoc = new PrimSeed(ID.COEINV, this::eoc, ref -> {
        // coeInv (A : I -> Type) (phi : I) : A 1 -> A 0
        var varA = new LocalVar("A");
        var paramA = new Term.Param(varA, intervalToType(), true);
        var paramRestr = new Term.Param(new LocalVar("i"), IntervalTerm.INSTANCE, true);
        var result = familyRightToLeft(new RefTerm(varA));

        return new PrimDef(ref, ImmutableSeq.of(paramA, paramRestr), result, ID.COEINV);
      }, ImmutableSeq.of(ID.I));

      @Contract("_, _ -> new")
      private @NotNull Term eoc(@NotNull PrimCall prim, @NotNull TyckState state) {
        var type = prim.args().get(0).term();
        var restr = prim.args().get(1).term();
        var u = new LocalVar("u");
        var paramU = new Term.Param(u, new AppTerm(type, new Arg<>(FormulaTerm.RIGHT, true)), true);
        return new LamTerm(paramU, new AppTerm(CoeTerm.coeInv(type, AyaRestrSimplifier.INSTANCE.isOne(restr)), new Arg<>(new RefTerm(u), true)));
      }

      public final @NotNull PrimDef.PrimSeed eocFill = new PrimSeed(ID.COEINVFILL, this::eocFill, ref -> {
        // coeInvFill (A : I -> Type) (phi : I) : Pi (u : A 1) -> Path A (coeInv A phi u) u
        return coeFillFactory(ref, ID.COEINVFILL, ID.COEINV, FormulaTerm.RIGHT);
      }, ImmutableSeq.of(ID.I, ID.COEINV));

      private @NotNull Term eocFill(@NotNull PrimCall prim, @NotNull TyckState state) {
        return coeFillHelper(prim, CoeTerm::coeFillInv, FormulaTerm.RIGHT);
      }

      private @NotNull Term coeFillHelper(
        @NotNull PrimCall prim,
        @NotNull TriFunction<Term, Restr<Term>, Term, Term> filler,
        @NotNull FormulaTerm start
      ) {
        // from hcomp.pdf:
        // Γ ⊢ u : A(i/<start>)
        // ----------------------------------------------
        // Γ, i : I ⊢ transFill^i A ϕ u := <filler>^y A ϕ u
        // or take coeFill for example:
        // Γ, i : I ⊢ transFill^i A ϕ u := trans^y A(i/i ∧ y) (ϕ ∨ (i = 0)) u
        var type = prim.args().get(0).term();
        var restr = prim.args().get(1).term();
        var i = new LocalVar("i");
        var u = new LocalVar("u");
        var fill = filler.apply(type, AyaRestrSimplifier.INSTANCE.isOne(restr), new RefTerm(i));
        var path = new PLamTerm(ImmutableSeq.of(i), new AppTerm(fill, new Arg<>(new RefTerm(u), true)));
        var paramU = new Term.Param(u, new AppTerm(type, new Arg<>(start, true)), true);
        return new LamTerm(paramU, path);
      }

      private @NotNull PrimDef coeFillFactory(
        @NotNull DefVar<PrimDef, TeleDecl.PrimDecl> ref,
        ID coeFill, ID coe, @NotNull FormulaTerm start
      ) {
        // <coeFill> (A : I -> Type) (phi : I) : Pi (u : A <start>) -> Path (\i => A <interval i>) u (<coe> A phi u)
        var varA = new LocalVar("A");
        var paramA = new Term.Param(varA, intervalToType(), true);
        var varPhi = new LocalVar("phi");
        var paramPhi = new Term.Param(varPhi, IntervalTerm.INSTANCE, true);
        var varU = new LocalVar("u");
        var paramU = new Term.Param(varU, new AppTerm(new RefTerm(varA), new Arg<>(start, true)), true);
        var i = new LocalVar("i");
        var path = new PathTerm(
          ImmutableSeq.of(i),
          new AppTerm(new RefTerm(varA), new Arg<>(new RefTerm(i), true)),
          new Partial.Split<>(ImmutableSeq.of(
            new Restr.Side<>(new Restr.Conj<>(ImmutableSeq.of(new Restr.Cond<>(new RefTerm(i), false))), new RefTerm(varU)),
            new Restr.Side<>(new Restr.Conj<>(ImmutableSeq.of(new Restr.Cond<>(new RefTerm(i), true))), new AppTerm(getCall(coe, ImmutableSeq.of(
              new Arg<>(new RefTerm(varA), true),
              new Arg<>(new RefTerm(varPhi), true))),
              new Arg<>(new RefTerm(varU), true)))
          )));
        var result = new PiTerm(paramU, path);
        return new PrimDef(ref, ImmutableSeq.of(paramA, paramPhi), result, coeFill);
      }

      public final @NotNull PrimDef.PrimSeed partialType =
        new PrimSeed(ID.PARTIAL,
          (prim, state) -> {
            var iExp = prim.args().get(0).term();
            var ty = prim.args().get(1).term();

            return new PartialTyTerm(ty, AyaRestrSimplifier.INSTANCE.isOne(iExp));
          },
          ref -> new PrimDef(
            ref,
            ImmutableSeq.of(
              new Term.Param(new LocalVar("phi"), IntervalTerm.INSTANCE, true),
              new Term.Param(new LocalVar("A"), Type0, true)
            ),
            SortTerm.Set0, ID.PARTIAL),
          ImmutableSeq.of(ID.I));
      private final @NotNull PrimDef.PrimSeed hcomp = new PrimSeed(ID.HCOMP, this::hcomp, ref -> {
        var varA = new LocalVar("A");
        var paramA = new Term.Param(varA, Type0, false);
        var varPhi = new LocalVar("phi");
        var paramRestr = new Term.Param(varPhi, IntervalTerm.INSTANCE, false);
        var varU = new LocalVar("u");
        var paramFuncU = new Term.Param(varU,
          new PiTerm(
            new Term.Param(LocalVar.IGNORED, IntervalTerm.INSTANCE, true),
            new PartialTyTerm(new RefTerm(varA), AyaRestrSimplifier.INSTANCE.isOne(new RefTerm(varPhi)))),
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

      private @NotNull PrimSeed formula(ID id, Function<PrimCall, Term> unfold, String... tele) {
        return new PrimSeed(id, (prim, state) -> unfold.apply(prim), ref -> new PrimDef(
          ref,
          ImmutableSeq.of(tele).map(n -> new Term.Param(new LocalVar(n), IntervalTerm.INSTANCE, true)),
          IntervalTerm.INSTANCE,
          id
        ), ImmutableSeq.of(ID.I));
      }

      private Term sub(@NotNull PrimCall prim, @NotNull TyckState tyckState) {
        var A = prim.args().get(0).term();
        var phi = prim.args().get(1).term();
        var u = prim.args().get(2).term();

        if (u instanceof PartialTerm(var partial, var rhs))
          // rhs == A
          return new SubTerm(A, AyaRestrSimplifier.INSTANCE.isOne(phi), partial);
        else return prim;
      }

      /**
       * @see #inS
       * @see #outS
       */
      private Term insideOut(@NotNull PrimCall prim, @NotNull TyckState tyckState) {
        var phi = prim.args().get(1).term();
        var u = prim.args().last().term();
        var kind = prim.id() == ID.INS ? InOutTerm.Kind.In : InOutTerm.Kind.Out;
        return InOutTerm.make(phi, u, kind);
      }

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

      private @NotNull Term hcomp(@NotNull PrimCall prim, @NotNull TyckState state) {
        var A = prim.args().get(0).term();
        var phi = prim.args().get(1).term();
        var u = prim.args().get(2).term();
        var u0 = prim.args().get(3).term();
        return new HCompTerm(A, AyaRestrSimplifier.INSTANCE.isOne(phi), u, u0);
      }
    }

    public @NotNull PrimDef factory(@NotNull ID name, @NotNull DefVar<PrimDef, TeleDecl.PrimDecl> ref) {
      assert suppressRedefinition() || !have(name);
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

  public final @NotNull DefVar<@NotNull PrimDef, TeleDecl.PrimDecl> ref;
  public final @NotNull ID id;

  public @NotNull DefVar<@NotNull PrimDef, TeleDecl.PrimDecl> ref() {
    return ref;
  }
}
