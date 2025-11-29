// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.states.primitive;

import kala.collection.Map;
import kala.collection.immutable.ImmutableMap;
import kala.collection.immutable.ImmutableSeq;
import kala.control.Option;
import kala.tuple.Tuple;
import org.aya.generic.term.DTKind;
import org.aya.normalize.Normalizer;
import org.aya.states.TyckState;
import org.aya.syntax.compile.JitPrim;
import org.aya.syntax.concrete.stmt.decl.PrimDecl;
import org.aya.syntax.core.Closure;
import org.aya.syntax.core.annotation.Closed;
import org.aya.syntax.core.def.PrimDef;
import org.aya.syntax.core.def.PrimDefLike;
import org.aya.syntax.core.term.*;
import org.aya.syntax.core.term.call.PrimCall;
import org.aya.syntax.core.term.repr.StringTerm;
import org.aya.syntax.core.term.xtt.CoeTerm;
import org.aya.syntax.core.term.xtt.DimTerm;
import org.aya.syntax.core.term.xtt.EqTerm;
import org.aya.syntax.ref.DefVar;
import org.aya.syntax.ref.LocalVar;
import org.aya.util.ForLSP;
import org.jetbrains.annotations.NotNull;

import java.util.EnumMap;
import java.util.function.BiFunction;
import java.util.function.Function;

import static org.aya.syntax.core.def.PrimDef.ID;
import static org.aya.syntax.core.def.PrimDef.familyI2J;
import static org.aya.syntax.core.term.SortTerm.Type0;

public class PrimFactory {
  private final @NotNull Map<@NotNull ID, @NotNull PrimSeed> seeds;
  private final @NotNull EnumMap<@NotNull ID, @NotNull PrimDefLike> defs = new EnumMap<>(ID.class);

  public PrimFactory() {
    seeds = ImmutableMap.from(ImmutableSeq.of(
      stringType,
      stringConcat,
      intervalType,
      pathType,
      coe
    ).map(seed -> Tuple.of(seed.name, seed)));
  }

  public void definePrim(PrimDefLike prim) {
    assert !isForbiddenRedefinition(prim.id(), prim instanceof JitPrim);
    defs.put(prim.id(), prim);
  }

  @FunctionalInterface
  public interface Unfolder extends BiFunction<@Closed @NotNull PrimCall, @NotNull TyckState, @Closed @NotNull Term> { }

  public record PrimSeed(
    @NotNull ID name,
    @NotNull Unfolder unfold,
    @NotNull Function<@NotNull DefVar<PrimDef, PrimDecl>, @NotNull PrimDef> supplier,
    @NotNull ImmutableSeq<@NotNull ID> dependency
  ) {
    public @NotNull PrimDef supply(@NotNull DefVar<PrimDef, PrimDecl> ref) {
      return supplier.apply(ref);
    }
  }

  final @NotNull PrimSeed coe = new PrimSeed(ID.COE, (prim, _) -> {
    // cause prim is Closed
    @Closed var args = prim.args();
    return new CoeTerm(closureParam(args.get(2)), args.get(0), args.get(1));
  }, ref -> {
    // coe (r s : I) (A : I -> Type) : A r -> A s
    var telescope = ImmutableSeq.of(
      intervalParam("r"),
      intervalParam("s"),
      new Param("A", intervalToType(), true));
    var r = LocalVar.generate("r");
    var s = LocalVar.generate("s");
    var A = LocalVar.generate("A");
    // Eta-expanded A
    var closureA = new Closure.Locns(new AppTerm(new FreeTerm(A), new LocalTerm(0)));
    var result = familyI2J(closureA, new FreeTerm(r), new FreeTerm(s))
      .bindTele(ImmutableSeq.of(r, s, A).view());

    return new PrimDef(ref, telescope, result, ID.COE);
  }, ImmutableSeq.of(ID.I));

  final @NotNull PrimSeed pathType = new PrimSeed(ID.PATH, (prim, _) -> {
    // cause prim is Closed
    @Closed var args = prim.args();
    return new EqTerm(closureParam(args.get(0)), args.get(1), args.get(2));
  }, ref -> {
    // (A : I -> Type) (a : A 0) (b : A 1) : Type
    var paramA = new Param("A", intervalToType(), true);
    var paramLeft = new Param("a", new AppTerm(new LocalTerm(0), DimTerm.I0), true);
    var paramRight = new Param("b", new AppTerm(new LocalTerm(1), DimTerm.I1), true);
    return new PrimDef(ref, ImmutableSeq.of(paramA, paramLeft, paramRight), Type0, ID.PATH);
  }, ImmutableSeq.of(ID.I));

  private static @Closed @NotNull Closure closureParam(@Closed @NotNull Term term) {
    var var = LocalVar.generate("disappearing");
    return AppTerm.make(term, new FreeTerm(var)).bind(var);
  }

  final @NotNull PrimSeed stringType =
    new PrimSeed(ID.STRING, (prim, _) -> prim,
      ref -> new PrimDef(ref, Type0, ID.STRING), ImmutableSeq.empty());

  final @NotNull PrimSeed stringConcat =
    new PrimSeed(ID.STRCONCAT, PrimFactory::concat, ref -> new PrimDef(
      ref,
      ImmutableSeq.of(
        new Param("str1", getCall(ID.STRING), true),
        new Param("str2", getCall(ID.STRING), true)
      ),
      getCall(ID.STRING),
      ID.STRCONCAT
    ), ImmutableSeq.of(ID.STRING));

  private static @NotNull Term concat(@Closed @NotNull PrimCall prim, @NotNull TyckState state) {
    var norm = new Normalizer(state);
    var first = norm.apply(prim.args().get(0));
    var second = norm.apply(prim.args().get(1));

    if (first instanceof StringTerm(var str1) && second instanceof StringTerm(var str2)) {
      return new StringTerm(str1 + str2);
    }

    return new PrimCall(prim.ref(), prim.ulift(), ImmutableSeq.of(first, second));
  }

  /*
  private final @NotNull PrimSeed hcomp = new PrimSeed(ID.HCOMP, this::hcomp, ref -> {
    var varA = new LocalVar("A");
    var paramA = new Term.Param(varA, Type0, false);
    var restr = IntervalTerm.paramImplicit("phi");
    var varU = new LocalVar("u");
    var paramFuncU = new Term.Param(varU,
      new PiTerm(
        IntervalTerm.param(LocalVar.IGNORED),
        new PartialTyTerm(new RefTerm(varA), AyaRestrSimplifier.INSTANCE.isOne(restr.toTerm()))),
      true);
    var varU0 = new LocalVar("u0");
    var paramU0 = new Term.Param(varU0, new RefTerm(varA), true);
    var result = new RefTerm(varA);
    return new PrimDef(
      ref,
      ImmutableSeq.of(paramA, restr, paramFuncU, paramU0),
      result,
      ID.HCOMP
    );
  }, ImmutableSeq.of(ID.I));

  private @NotNull Term hcomp(@NotNull PrimCall prim, @NotNull TyckState state) {
    var A = prim.args().get(0);
    var phi = prim.args().get(1);
    var u = prim.args().get(2);
    var u0 = prim.args().get(3);
    return new HCompTerm(A, AyaRestrSimplifier.INSTANCE.isOne(phi), u, u0);
  }
  */

  public final @NotNull PrimSeed intervalType = new PrimSeed(ID.I,
    ((prim, _) -> prim),
    ref -> new PrimDef(ref, SortTerm.ISet, ID.I),
    ImmutableSeq.empty());

  public @NotNull PrimDefLike factory(@NotNull ID name, @NotNull DefVar<PrimDef, PrimDecl> ref) {
    var rst = new PrimDef.Delegate(seeds.get(name).supply(ref).ref());
    definePrim(rst);
    return rst;
  }

  /// `I -> Type`
  public @NotNull Term intervalToType() {
    return new DepTypeTerm(DTKind.Pi, getCall(ID.I), Closure.mkConst(Type0));
  }

  public Param intervalParam(String r) {
    return new Param(r, getCall(ID.I), true);
  }

  public @NotNull PrimCall getCall(@NotNull ID id, @NotNull ImmutableSeq<Term> args) {
    return new PrimCall(getOption(id).get(), 0, args);
  }

  public @NotNull PrimCall getCall(@NotNull ID id) {
    return new PrimCall(getOption(id).get());
  }

  public @NotNull Option<PrimDefLike> getOption(@NotNull ID name) {
    return Option.ofNullable(defs.get(name));
  }

  public boolean have(@NotNull ID name) {
    return defs.containsKey(name);
  }

  /**
   * Whether this definition is a redefinition that should be treated as error.
   * There are two cases where a redefinition is allowed:
   * <ul>
   *   <li>When we are working in an LSP, and users can reload a file to redefine things.</li>
   *   <li>When we are serializing a file, which we will deserialize immediately, and this will
   *     replace the existing PrimDefs with their JIT-compiled version.</li>
   * </ul>
   *
   * @return true if redefinition is forbidden.
   */
  @ForLSP public boolean isForbiddenRedefinition(@NotNull PrimDef.ID id, boolean isJit) {
    if (isJit)
      return have(id) && defs.get(id) instanceof JitPrim;
    else return have(id);
  }

  public @NotNull Option<ImmutableSeq<@NotNull ID>> checkDependency(@NotNull ID name) {
    return seeds.getOption(name).map(seed -> seed.dependency().filterNot(this::have));
  }

  public @NotNull Term unfold(@Closed @NotNull PrimCall primCall, @NotNull TyckState state) {
    var id = primCall.ref().id();
    return seeds.get(id).unfold.apply(primCall, state);
  }

  public void clear() { defs.clear(); }
  public void clear(@NotNull ID name) { defs.remove(name); }
}
