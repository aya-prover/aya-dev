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
import org.aya.api.ref.LocalVar;
import org.aya.api.util.Arg;
import org.aya.api.util.NormalizeMode;
import org.aya.concrete.stmt.Decl;
import org.aya.core.sort.Sort;
import org.aya.core.term.*;
import org.aya.generic.Level;
import org.aya.util.Constants;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;
import java.util.function.Supplier;

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
    this(telescope, levels, result, unfold, DefVar.empty(name));
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

  public static class PrimFactory {
    private final @NotNull MutableMap<@NotNull String, @NotNull PrimDef> defs = MutableMap.create();
    public static final @NotNull PrimFactory INSTANCE = new PrimFactory();

    private PrimFactory() {
    }

    private static @NotNull Term arcoe(CallTerm.@NotNull Prim prim) {
      var args = prim.args();
      var argBase = args.get(1).term();
      var argI = args.get(2).term();
      var left = INSTANCE.getOption(LEFT);
      if (argI instanceof CallTerm.Prim primCall && left.isNotEmpty() && primCall.ref() == left.get().ref)
        return argBase;
      var argA = args.get(0).term();
      if (argA instanceof IntroTerm.Lambda lambda && lambda.body()
        .normalize(NormalizeMode.NF)
        .findUsages(lambda.param().ref()) == 0)
        return argBase;
      return prim;
    }

    private static @NotNull Term invol(CallTerm.@NotNull Prim prim) {
      var arg = prim.args().get(0).term();
      if (arg instanceof CallTerm.Prim primCall) {
        var left = INSTANCE.getOption(LEFT);
        var right = INSTANCE.getOption(RIGHT);
        assert left.isNotEmpty() && right.isNotEmpty();
        if (primCall.ref() == left.get().ref)
          return new CallTerm.Prim(right.get().ref, ImmutableSeq.empty(), ImmutableSeq.empty());
        if (primCall.ref() == right.get().ref)
          return new CallTerm.Prim(left.get().ref, ImmutableSeq.empty(), ImmutableSeq.empty());
      }
      return prim;
    }

    private static final @NotNull Map<@NotNull String, @NotNull Supplier<@NotNull PrimDef>> SUPPLIERS;

    public static final @NotNull Map<@NotNull String, @NotNull Function<CallTerm.@NotNull Prim, @NotNull Term>> UNFOLD
      = ImmutableMap.ofEntries(
      Tuple.of(INTERVAL, prim -> prim),
      Tuple.of(LEFT, prim -> prim),
      Tuple.of(RIGHT, prim -> prim),
      Tuple.of(ARCOE, PrimFactory::arcoe),
      Tuple.of(INVOL, PrimFactory::invol)
    );

    private static final @NotNull Map<@NotNull String, @NotNull ImmutableSeq<@NotNull String>> DEPENDENCY = ImmutableMap.ofEntries(
      Tuple.of(INTERVAL, ImmutableSeq.empty()),
      Tuple.of(LEFT, ImmutableSeq.of(INTERVAL)),
      Tuple.of(RIGHT, ImmutableSeq.of(INTERVAL)),
      Tuple.of(ARCOE, ImmutableSeq.empty()),
      Tuple.of(INVOL, ImmutableSeq.empty())
    );

    static {
      Supplier<CallTerm.Prim> intervalCallSupplier =
        () -> new CallTerm.Prim(INSTANCE.getOrCreate(INTERVAL).ref(),
          ImmutableSeq.empty(), ImmutableSeq.empty());


      SUPPLIERS = ImmutableMap.ofEntries(
        Tuple.of(INTERVAL, () -> new PrimDef(ImmutableSeq.empty(), ImmutableSeq.empty(),
          new FormTerm.Univ(new Sort(new Level.Constant<>(0))), UNFOLD.get(INTERVAL), INTERVAL)),
        Tuple.of(LEFT, () -> new PrimDef(ImmutableSeq.empty(),
          ImmutableSeq.empty(), intervalCallSupplier.get(), UNFOLD.get(LEFT), LEFT)),
        Tuple.of(RIGHT, () -> new PrimDef(ImmutableSeq.empty(), ImmutableSeq.empty(),
          intervalCallSupplier.get(), UNFOLD.get(RIGHT), RIGHT)),
        Tuple.of(ARCOE, () -> {
          var paramA = new LocalVar("A");
          var paramIToATy = new Term.Param(new LocalVar(Constants.ANONYMOUS_PREFIX), intervalCallSupplier.get(), true);
          var paramI = new LocalVar("i");
          var universe = new Sort.LvlVar("u", null);
          var result = new FormTerm.Univ(new Sort(new Level.Reference<>(universe)));
          var paramATy = new FormTerm.Pi(paramIToATy, result);
          var aRef = new RefTerm(paramA, paramATy);
          var left = INSTANCE.getOrCreate(LEFT);
          var baseAtLeft = new ElimTerm.App(aRef, new Arg<>(new CallTerm.Prim(left.ref, ImmutableSeq.empty(), ImmutableSeq.empty()), true));
          return new PrimDef(
            ImmutableSeq.of(
              new Term.Param(paramA, paramATy, true),
              new Term.Param(new LocalVar("base"), baseAtLeft, true),
              new Term.Param(paramI, intervalCallSupplier.get(), true)
            ),
            ImmutableSeq.of(universe),
            new ElimTerm.App(aRef, new Arg<>(new RefTerm(paramI, intervalCallSupplier.get()), true)),
            UNFOLD.get(ARCOE), "arcoe");
        }),
        Tuple.of(INVOL, () -> {
          CallTerm.Prim intervalCall = new CallTerm.Prim(INSTANCE.getOrCreate(INTERVAL).ref(), ImmutableSeq.empty(), ImmutableSeq.empty());
          return new PrimDef(
            ImmutableSeq.of(new Term.Param(new LocalVar("i"), intervalCallSupplier.get(), true)), ImmutableSeq.empty(),
            intervalCall, UNFOLD.get(INVOL), INVOL);
        })
      );
    }

    public @NotNull Option<PrimDef> factory(
      @NotNull String name
    ) {
      assert !have(name);
      var rst = SUPPLIERS.getOption(name).map(Supplier::get);

      if (rst.isNotEmpty()) {
        defs.set(name, rst.get());
      }

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

    public @NotNull Option<ImmutableSeq<@NotNull String>> checkDependency(@NotNull String name) {
      return DEPENDENCY.getOption(name).map(d -> d.filterNot(this::have));
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
