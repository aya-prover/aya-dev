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
import org.aya.core.visitor.Normalizer;
import org.aya.generic.Level;
import org.aya.util.Constants;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

/**
 * @author ice1000
 */
public final class PrimDef extends TopLevelDef {
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

  @Override public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
    return visitor.visitPrim(this, p);
  }

  public @NotNull Term unfold(@NotNull CallTerm.Prim primCall) {
    return unfold.apply(primCall);
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

  public record PrimFactory(
    @NotNull MutableMap<@NotNull String, @NotNull PrimDef> defs
  ) {
    private static @NotNull Function<CallTerm.@NotNull Prim, @NotNull Term> arcoe(@NotNull PrimDef.PrimFactory factory) {
      return (prim) -> {
        var args = prim.args();
        var argBase = args.get(1).term();
        var argI = args.get(2).term();
        var left = factory.getOption(LEFT);
        if (argI instanceof CallTerm.Prim primCall && left.isNotEmpty() && primCall.ref() == left.get().ref)
          return argBase;
        var argA = args.get(0).term();
        if (argA instanceof IntroTerm.Lambda lambda && lambda.body().findUsages(lambda.param().ref()) == 0)
          return argBase;
        return prim;
      };
    }

    private static @NotNull Function<CallTerm.@NotNull Prim, @NotNull Term> invol(@NotNull PrimDef.PrimFactory factory) {
      return prim -> {
        var arg = prim.args().get(0).term();
        if (arg instanceof CallTerm.Prim primCall) {
          var left = factory.getOption(LEFT);
          var right = factory.getOption(RIGHT);
          assert left.isNotEmpty() && right.isNotEmpty();
          if (primCall.ref() == left.get().ref)
            return new CallTerm.Prim(right.get().ref, ImmutableSeq.empty(), ImmutableSeq.empty());
          if (primCall.ref() == right.get().ref)
            return new CallTerm.Prim(left.get().ref, ImmutableSeq.empty(), ImmutableSeq.empty());
        }
        return prim;
      };
    }

    private static final @NotNull Map<@NotNull String, @NotNull Function<@NotNull PrimFactory, @NotNull PrimDef>> SUPPLIERS;

    static {
      Function<@NotNull PrimFactory, CallTerm.Prim> intervalCallSupplier =
        (factory) -> new CallTerm.Prim(factory.getOrCreate(INTERVAL).ref(),
          ImmutableSeq.empty(), ImmutableSeq.empty());

      SUPPLIERS = ImmutableMap.ofEntries(
        Tuple.of(INTERVAL, (factory) -> new PrimDef(ImmutableSeq.empty(), ImmutableSeq.empty(),
          new FormTerm.Univ(new Sort(new Level.Constant<>(0), Sort.INF_LVL)), prim -> prim, INTERVAL)),
        Tuple.of(LEFT, (factory) -> new PrimDef(ImmutableSeq.empty(),
          ImmutableSeq.empty(), intervalCallSupplier.apply(factory), prim -> prim, LEFT)),
        Tuple.of(RIGHT, (factory) -> new PrimDef(ImmutableSeq.empty(), ImmutableSeq.empty(),
          intervalCallSupplier.apply(factory), prim -> prim, RIGHT)),
        Tuple.of(ARCOE, (factory) -> {
          var paramA = new LocalVar("A");
          var paramIToATy = new Term.Param(new LocalVar(Constants.ANONYMOUS_PREFIX), intervalCallSupplier.apply(factory), true);
          var paramI = new LocalVar("i");
          var homotopy = new Sort.LvlVar("h", LevelGenVar.Kind.Homotopy, null);
          var universe = new Sort.LvlVar("u", LevelGenVar.Kind.Universe, null);
          var result = new FormTerm.Univ(new Sort(new Level.Reference<>(universe), new Level.Reference<>(homotopy)));
          var paramATy = new FormTerm.Pi(paramIToATy, result);
          var aRef = new RefTerm(paramA, paramATy);
          var left = factory.getOrCreate(LEFT);
          var baseAtLeft = new ElimTerm.App(aRef, Arg.explicit(
            new CallTerm.Prim(left.ref, ImmutableSeq.of(), ImmutableSeq.empty())));
          return new PrimDef(
            ImmutableSeq.of(
              new Term.Param(paramA, paramATy, true),
              new Term.Param(new LocalVar("base"), baseAtLeft, true),
              new Term.Param(paramI, intervalCallSupplier.apply(factory), true)
            ),
            ImmutableSeq.of(homotopy, universe),
            new ElimTerm.App(aRef, Arg.explicit(new RefTerm(paramI, intervalCallSupplier.apply(factory)))),
            PrimFactory.arcoe(factory), "arcoe");
        }),
        Tuple.of(INVOL, (factory) -> {
          CallTerm.Prim intervalCall = new CallTerm.Prim(factory.getOrCreate(INTERVAL).ref(), ImmutableSeq.empty(), ImmutableSeq.empty());
          return new PrimDef(
            ImmutableSeq.of(new Term.Param(new LocalVar("i"), intervalCallSupplier.apply(factory), true)), ImmutableSeq.of(),
            intervalCall, PrimFactory.invol(factory), INVOL);
        })
      );
    }

    public @NotNull Option<PrimDef> factory(
      @NotNull String name
    ) {
      if (have(name)) {
        return Option.none();
      }
      var rst = SUPPLIERS.getOption(name).map(
        (f) -> f.apply(this)
      );
      if (rst.isNotEmpty()) {
        defs.set(name, rst.get());
      }

      return rst;
    }

    public static @NotNull PrimDef.PrimFactory create() {
      var rst = new PrimFactory(MutableMap.create());
      Normalizer.INSTANCE.primFactory = rst;
      return rst;
    }

    public @NotNull Option<PrimDef> getOption(@NotNull String name) {
      return defs.getOption(name);
    }

    public boolean have(@NotNull String name) {
      return defs.containsKey(name);
    }

    public @NotNull PrimDef getOrCreate(@NotNull String name) {
      return getOption(name).getOrElse(() -> factory(name).get());
    }

    public static final @NotNull ImmutableSeq<String> LEFT_RIGHT = ImmutableSeq.of(LEFT, RIGHT);

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

  public static final @NotNull String INTERVAL = "I";
  public static final @NotNull String LEFT = "left";
  public static final @NotNull String RIGHT = "right";
  /** Short for <em>Arend coe</em>. */
  public static final @NotNull String ARCOE = "arcoe";
  public static final @NotNull String INVOL = "invol";

  public final @NotNull Function<CallTerm.@NotNull Prim, @NotNull Term> unfold;
  public final @NotNull DefVar<@NotNull PrimDef, Decl.PrimDecl> ref;

  /**
   *
   */
  public PrimDef(
    @NotNull ImmutableSeq<Term.Param> telescope, @NotNull ImmutableSeq<Sort.LvlVar> levels,
    @NotNull Term result, @NotNull Function<CallTerm.@NotNull Prim, @NotNull Term> unfold,
    @NotNull DefVar<@NotNull PrimDef, Decl.PrimDecl> ref
  ) {
    super(telescope, result, levels);
    this.unfold = unfold;
    this.ref = ref;
  }

  public @NotNull DefVar<@NotNull PrimDef, Decl.PrimDecl> ref() {
    return ref;
  }
}
