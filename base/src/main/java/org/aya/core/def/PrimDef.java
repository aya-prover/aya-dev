// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.def;

import kala.collection.Map;
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
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * @author ice1000
 */
public final class PrimDef extends TopLevelDef {
  public PrimDef(
    @NotNull ImmutableSeq<Term.Param> telescope,
    @NotNull ImmutableSeq<Sort.LvlVar> levels,
    @NotNull Term result, @NotNull ID name
  ) {
    this(telescope, levels, result, DefVar.empty(name.id));
    ref.core = this;
  }

  public PrimDef(@NotNull Term result, @NotNull ID name) {
    this(ImmutableSeq.empty(), ImmutableSeq.empty(), result, name);
  }

  @Override public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
    return visitor.visitPrim(this, p);
  }

  public @NotNull Term unfold(@NotNull CallTerm.Prim primCall) {
    return Factory.INSTANCE.unfold(Objects.requireNonNull(ID.find(ref.name())), primCall);
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
    @NotNull Function<CallTerm.@NotNull Prim, @NotNull Term> unfold,
    @NotNull Supplier<@NotNull PrimDef> supplier,
    @NotNull ImmutableSeq<@NotNull ID> dependency
  ) {
    public @NotNull PrimDef supply() {
      return supplier.get();
    }

    // Interval
    public static CallTerm.Prim intervalCall() {
      return new CallTerm.Prim(Factory.INSTANCE.getOrCreate(ID.INTERVAL).ref(),
        ImmutableSeq.empty(), ImmutableSeq.empty());
    }

    public static final @NotNull PrimSeed INTERVAL = new PrimSeed(
      ID.INTERVAL,
      prim -> prim,
      () -> new PrimDef(FormTerm.Univ.ZERO, ID.INTERVAL),
      ImmutableSeq.empty()
    );
    public static final @NotNull PrimDef.PrimSeed LEFT = new PrimSeed(
      ID.LEFT,
      prim -> prim,
      () -> new PrimDef(intervalCall(), ID.LEFT),
      ImmutableSeq.of(ID.INTERVAL)
    );

    // Right
    public static final @NotNull PrimDef.PrimSeed RIGHT = new PrimSeed(
      ID.RIGHT,
      prim -> prim,
      () -> new PrimDef(intervalCall(), ID.RIGHT),
      ImmutableSeq.of(ID.INTERVAL)
    );

    // Arcoe
    private static @NotNull Term arcoe(CallTerm.@NotNull Prim prim) {
      var args = prim.args();
      var argBase = args.get(1).term();
      var argI = args.get(2).term();
      var left = Factory.INSTANCE.getOption(ID.LEFT);
      if (argI instanceof CallTerm.Prim primCall && left.isNotEmpty() && primCall.ref() == left.get().ref)
        return argBase;
      var argA = args.get(0).term();
      if (argA instanceof IntroTerm.Lambda lambda && lambda.body()
        .normalize(NormalizeMode.NF)
        .findUsages(lambda.param().ref()) == 0)
        return argBase;
      return prim;
    }

    public static final @NotNull PrimDef.PrimSeed ARCOE = new PrimSeed(ID.ARCOE, PrimSeed::arcoe, () -> {
      var paramA = new LocalVar("A");
      var paramIToATy = new Term.Param(new LocalVar(Constants.ANONYMOUS_PREFIX), intervalCall(), true);
      var paramI = new LocalVar("i");
      var universe = new Sort.LvlVar("u", null);
      var result = new FormTerm.Univ(new Sort(new Level.Reference<>(universe)));
      var paramATy = new FormTerm.Pi(paramIToATy, result);
      var aRef = new RefTerm(paramA, paramATy);
      var left = Factory.INSTANCE.getOrCreate(ID.LEFT);
      var baseAtLeft = new ElimTerm.App(aRef, new Arg<>(new CallTerm.Prim(left.ref, ImmutableSeq.empty(), ImmutableSeq.empty()), true));
      return new PrimDef(
        ImmutableSeq.of(
          new Term.Param(paramA, paramATy, true),
          new Term.Param(new LocalVar("base"), baseAtLeft, true),
          new Term.Param(paramI, intervalCall(), true)
        ),
        ImmutableSeq.of(universe),
        new ElimTerm.App(aRef, new Arg<>(new RefTerm(paramI, intervalCall()), true)),
        ID.ARCOE
      );
    }, ImmutableSeq.empty());

    // Invol
    private static @NotNull Term invol(CallTerm.@NotNull Prim prim) {
      var arg = prim.args().get(0).term();
      if (arg instanceof CallTerm.Prim primCall) {
        var left = Factory.INSTANCE.getOption(ID.LEFT);
        var right = Factory.INSTANCE.getOption(ID.RIGHT);
        assert left.isNotEmpty() && right.isNotEmpty();
        if (primCall.ref() == left.get().ref)
          return new CallTerm.Prim(right.get().ref, ImmutableSeq.empty(), ImmutableSeq.empty());
        if (primCall.ref() == right.get().ref)
          return new CallTerm.Prim(left.get().ref, ImmutableSeq.empty(), ImmutableSeq.empty());
      }
      return prim;
    }

    public static final @NotNull PrimDef.PrimSeed INVOL = new PrimSeed(ID.INVOL, PrimSeed::invol, () -> new PrimDef(
      ImmutableSeq.of(new Term.Param(new LocalVar("i"), intervalCall(), true)),
      ImmutableSeq.empty(),
      intervalCall(),
      ID.INVOL
    ), ImmutableSeq.empty());
  }

  public static class Factory {
    private final @NotNull MutableMap<@NotNull ID, @NotNull PrimDef> defs = MutableMap.create();
    public static final @NotNull PrimDef.Factory INSTANCE = new Factory();

    private Factory() {
    }

    private static final @NotNull Map<@NotNull ID, @NotNull PrimSeed> SEEDS = ImmutableSeq.of(
        PrimSeed.INTERVAL,
        PrimSeed.LEFT,
        PrimSeed.RIGHT,
        PrimSeed.ARCOE,
        PrimSeed.INVOL
      ).map(seed -> Tuple.of(seed.name, seed))
      .toImmutableMap();

    public @NotNull PrimDef factory(@NotNull ID name) {
      assert !have(name);
      var rst = SEEDS.get(name).supply();
      defs.set(name, rst);
      return rst;
    }

    public @NotNull Option<PrimDef> getOption(@NotNull ID name) {
      return defs.getOption(name);
    }

    public boolean have(@NotNull ID name) {
      return defs.containsKey(name);
    }

    public @NotNull PrimDef getOrCreate(@NotNull ID name) {
      return getOption(name).getOrElse(() -> factory(name));
    }

    public @NotNull Option<ImmutableSeq<@NotNull ID>> checkDependency(@NotNull ID name) {
      return SEEDS.getOption(name).map(seed -> seed.dependency().filterNot(this::have));
    }

    public @NotNull Term unfold(@NotNull ID name, @NotNull CallTerm.Prim primCall) {
      return SEEDS.get(name).unfold.apply(primCall);
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
    INVOL("invol");
    public final @NotNull @NonNls String id;

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

  private PrimDef(
    @NotNull ImmutableSeq<Term.Param> telescope, @NotNull ImmutableSeq<Sort.LvlVar> levels,
    @NotNull Term result, @NotNull DefVar<@NotNull PrimDef, Decl.PrimDecl> ref
  ) {
    super(telescope, result, levels);
    this.ref = ref;
  }

  public @NotNull DefVar<@NotNull PrimDef, Decl.PrimDecl> ref() {
    return ref;
  }
}
