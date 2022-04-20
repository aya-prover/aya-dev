// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.def;

import kala.collection.Map;
import kala.collection.immutable.ImmutableSeq;
import kala.control.Option;
import kala.tuple.Tuple;
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

  @Override public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
    return visitor.visitPrim(this, p);
  }

  public @NotNull Term unfold(@NotNull CallTerm.Prim primCall, @Nullable TyckState state) {
    return Objects.requireNonNull(state).primFactory().unfold(Objects.requireNonNull(ID.find(ref.name())), primCall, state);
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
  }

  public static class Factory {
    private final class Initializer {
      /** Arend's coe */
      private @NotNull Term arcoe(CallTerm.@NotNull Prim prim, @Nullable TyckState state) {
        var args = prim.args();
        var argBase = args.get(1);
        var argI = args.get(2);
        if (argI.term() instanceof PrimTerm.End end && end.left())
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
        var paramIToATy = new Term.Param(new LocalVar(Constants.ANONYMOUS_PREFIX), new FormTerm.Interval(), true);
        var paramI = new LocalVar("i");
        var result = new FormTerm.Univ(0);
        var paramATy = new FormTerm.Pi(paramIToATy, result);
        var aRef = new RefTerm(paramA, 0);
        var baseAtLeft = new ElimTerm.App(aRef, new Arg<>(new PrimTerm.End(PrimTerm.LEFT), true));
        return new PrimDef(
          ref,
          ImmutableSeq.of(
            new Term.Param(paramA, paramATy, true),
            new Term.Param(new LocalVar("base"), baseAtLeft, true),
            new Term.Param(paramI, new FormTerm.Interval(), true)
          ),
          new ElimTerm.App(aRef, new Arg<>(new RefTerm(paramI, 0), true)),
          ID.ARCOE
        );
      }, ImmutableSeq.empty());

      /** Involution, ~ in Cubical Agda */
      private @NotNull Term invol(CallTerm.@NotNull Prim prim, @Nullable TyckState state) {
        var arg = prim.args().get(0).term().normalize(state, NormalizeMode.WHNF);
        if (arg instanceof PrimTerm.End end) {
          return new PrimTerm.End(!end.val());
        } else {
          return new CallTerm.Prim(prim.ref(), 0, ImmutableSeq.of(new Arg<>(arg, true)));
        }
      }

      public final @NotNull PrimDef.PrimSeed INVOL = new PrimSeed(ID.INVOL, this::invol, ref -> new PrimDef(
        ref,
        ImmutableSeq.of(new Term.Param(new LocalVar("i"), new FormTerm.Interval(), true)),
        new FormTerm.Interval(),
        ID.INVOL
      ), ImmutableSeq.empty());

      /** <code>/\</code> in CCHM, <code>I.squeeze</code> in Arend */
      private @NotNull Term squeezeLeft(CallTerm.@NotNull Prim prim, @Nullable TyckState state) {
        var lhsArg = prim.args().get(0).term().normalize(state, NormalizeMode.WHNF);
        var rhsArg = prim.args().get(1).term().normalize(state, NormalizeMode.WHNF);
        if (lhsArg instanceof PrimTerm.End lhsEnd) {
          if (lhsEnd.left()) {
            return lhsEnd;
          } else {
            return rhsArg;
          }
        } else if (rhsArg instanceof PrimTerm.End rhsEnd) {
          if (rhsEnd.left()) {
            return rhsEnd;
          } else {
            return lhsArg;
          }
        }

        return prim;
      }

      public final @NotNull PrimDef.PrimSeed SQUEEZE_LEFT =
        new PrimSeed(ID.SQUEEZE_LEFT, this::squeezeLeft, ref -> new PrimDef(
          ref,
          ImmutableSeq.of(
            new Term.Param(new LocalVar("i"), new FormTerm.Interval(), true),
            new Term.Param(new LocalVar("j"), new FormTerm.Interval(), true)),
          new FormTerm.Interval(),
          ID.SQUEEZE_LEFT
        ), ImmutableSeq.empty());
    }

    private final @NotNull EnumMap<@NotNull ID, @NotNull PrimDef> defs = new EnumMap<>(ID.class);

    private final @NotNull Map<@NotNull ID, @NotNull PrimSeed> SEEDS;

    public Factory() {
      var init = new Initializer();
      SEEDS = ImmutableSeq.of(
          init.ARCOE,
          init.SQUEEZE_LEFT,
          init.INVOL
        ).map(seed -> Tuple.of(seed.name, seed))
        .toImmutableMap();
    }

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
    public void clear() {
      defs.clear();
    }
  }

  public enum ID {
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
