// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.primitive;

import kala.collection.SeqLike;
import kala.collection.immutable.ImmutableMap;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableLinkedList;
import kala.collection.mutable.MutableMap;
import kala.control.Either;
import kala.control.Option;
import kala.tuple.Tuple;
import kala.tuple.Tuple2;
import kala.value.MutableValue;
import org.aya.generic.Modifier;
import org.aya.syntax.core.def.*;
import org.aya.syntax.core.pat.Pat;
import org.aya.syntax.core.repr.*;
import org.aya.syntax.core.repr.CodeShape.*;
import org.aya.syntax.core.term.*;
import org.aya.syntax.core.term.call.Callable;
import org.aya.syntax.ref.AnyVar;
import org.aya.syntax.ref.DefVar;
import org.aya.util.Pair;
import org.aya.util.RepoLike;
import org.aya.util.error.Panic;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

/**
 * @author kiva
 */
public record ShapeMatcher(
  @NotNull Captures captures,
  @NotNull MutableMap<AnyVar, AnyVar> teleSubst,
  // --------
  @NotNull ImmutableMap<AnyDef, ShapeRecognition> discovered
) {

  public record Captures(
    @NotNull MutableMap<MomentId, AnyVar> map,
    @NotNull MutableValue<Captures> future
  ) implements RepoLike<Captures> {
    public static @NotNull Captures create() {
      return new Captures(MutableMap.create(), MutableValue.create());
    }

    public @NotNull ImmutableMap<GlobalId, AnyDef> extractGlobal() {
      return ImmutableMap.from(map.toImmutableSeq().view()
        .mapNotNull(x -> switch (x) {
          case Tuple2(GlobalId gid, DefVar<?, ?> dv) -> Tuple.of(gid, TyckAnyDef.make(dv.core));
          default -> null;
        }));
    }

    @Override public void setDownstream(@Nullable Captures downstream) {
      future.set(downstream);
    }

    public void fork() {
      RepoLike.super.fork(new Captures(MutableMap.from(map), MutableValue.create()));
    }

    /** closed with unmerged changes */
    public void discard() { RepoLike.super.merge(); }

    @Override public void merge() {
      var f = this.future.get();
      if (f != null) map.putAll(f.map);
      RepoLike.super.merge();
    }

    private @NotNull MutableMap<MomentId, AnyVar> choose() {
      var f = this.future.get();
      return f != null ? f.map : this.map;
    }

    public @NotNull AnyVar resolve(@NotNull MomentId id) {
      return choose().getOrThrow(id, () -> new Panic("Invalid moment id " + id));
    }

    public void put(@NotNull MomentId id, @NotNull AnyVar var) { choose().put(id, var); }
  }

  public ShapeMatcher() {
    this(ImmutableMap.empty());
  }

  public ShapeMatcher(@NotNull ImmutableMap<AnyDef, ShapeRecognition> discovered) {
    this(Captures.create(), MutableMap.create(), discovered);
  }

  public Option<ShapeRecognition> match(@NotNull AyaShape shape, @NotNull TyckDef def) {
    if (matchDecl(new MatchDecl(shape.codeShape(), def))) {
      return Option.some(new ShapeRecognition(shape, captures.extractGlobal()));
    }
    return Option.none();
  }

  record MatchDecl(@NotNull CodeShape shape, @NotNull TyckDef def) { }

  private boolean matchDecl(@NotNull MatchDecl params) {
    return switch (params) {
      case MatchDecl(DataShape dataShape, DataDef data) -> matchData(dataShape, data);
      case MatchDecl(FnShape fnShape, FnDef fn) -> matchFn(fnShape, fn);
      default -> false;
    };
  }

  private boolean matchFn(@NotNull FnShape shape, @NotNull FnDef def) {
    // match signature
    var teleResult = matchTele(shape.tele(), def.telescope())
      && matchTerm(shape.result(), def.result());
    if (!teleResult) return false;

    // match body
    return switch (new Pair<>(shape.body(), def.body())) {
      case Pair(Either.Left(var termShape), Either.Left(var term)) ->
        matchInside(() -> captures.put(shape.name(), def.ref()), () -> matchTerm(termShape, term));
      case Pair(Either.Right(var clauseShapes), Either.Right(var clauses)) -> {
        var mode = def.is(Modifier.Overlap) ? MatchMode.Sub : MatchMode.Eq;
        yield matchInside(() -> captures.put(shape.name(), def.ref()), () ->
          matchMany(mode, clauseShapes, clauses, this::matchClause));
      }
      default -> false;
    };
  }

  private boolean matchClause(@NotNull ClauseShape shape, @NotNull Term.Matching clause) {
    // match pats
    var patsResult = matchMany(MatchMode.OrderedEq, shape.pats(), clause.patterns(),
      this::matchPat);
    if (!patsResult) return false;
    return matchTerm(shape.body(), clause.body());
  }

  private boolean matchPat(@NotNull PatShape shape, @NotNull Pat pat) {
    if (shape == PatShape.Basic.Any) return true;
    if (pat instanceof Pat.ShapedInt shapedInt)
      pat = shapedInt.constructorForm();
    return switch (new Pair<>(shape, pat)) {
      case Pair(PatShape.Basic _/*Bind*/, Pat.Bind _) -> true;
      case Pair(PatShape.ConLike conLike, Pat.Con con) -> {
        boolean matched = true;

        if (conLike instanceof PatShape.ShapedCon shapedCon) {
          var data = captures.resolve(shapedCon.dataId());
          if (!(data instanceof DefVar<?, ?> defVar)) {
            throw new Panic("Invalid name: " + shapedCon.dataId());
          }

          var recognition = discovered.getOrThrow(new TyckAnyDef<>(defVar), () -> new Panic("Not a shaped data"));
          var realShapedCon = recognition.captures().getOrThrow(shapedCon.conId(), () ->
            new Panic("Invalid moment id: " + shapedCon.conId() + " in recognition" + recognition));

          matched = Objects.equals(realShapedCon, con.ref());
        }

        if (!matched) yield false;

        // We don't use `matchInside` here, because the context doesn't need to reset.
        yield matchMany(MatchMode.OrderedEq, conLike.innerPats(), con.args().view(),
          this::matchPat);
      }
      default -> false;
    };
  }

  private boolean matchData(@NotNull DataShape shape, @NotNull DataDef data) {
    if (!matchTele(shape.tele(), data.telescope())) return false;
    return matchInside(() -> captures.put(shape.name(), data.ref),
      () -> matchMany(MatchMode.Eq, shape.cons(), data.body,
        (s, c) -> captureIfMatches(s, c, this::matchCon, ConDef::ref)));
  }

  private boolean matchCon(@NotNull ConShape shape, @NotNull ConDef con) {
    return matchTele(shape.tele(), con.selfTele);
  }

  private boolean matchTerm(@NotNull TermShape shape, @NotNull Term term) {
    return switch (shape) {
      case TermShape.Any _ -> true;
      case TermShape.NameCall call when call.args().isEmpty() && term instanceof FreeTerm(var ref) ->
        captures.resolve(call.name()) == ref;
      case TermShape.DeBruijn(var index) -> term instanceof LocalTerm(var jndex) && index == jndex;
      case TermShape.Callable call when term instanceof Callable.Tele callable -> {
        var ref = (TyckAnyDef<?>) callable.ref();
        boolean success = switch (call) {
          case TermShape.NameCall nameCall -> captures.resolve(nameCall.name()) == ref.ref;
          case TermShape.ShapeCall shapeCall -> {
            if (callable.ref() instanceof TyckAnyDef<?> wrapper) {
              yield captureIfMatches(shapeCall.name(), wrapper.ref, () ->
                discovered.getOption(wrapper).map(x -> x.shape().codeShape()).getOrNull() == shapeCall.shape());
            }

            yield false;
          }
          case TermShape.ConCall conCall -> ref.equals(resolveCon(conCall.dataId(), conCall.conId()));
        };

        if (!success) yield false;
        yield matchMany(MatchMode.OrderedEq, call.args(), callable.args(), this::matchTerm);
      }
      case TermShape.Sort sort when term instanceof SortTerm sortTerm -> {
        // kind is null -> any sort
        if (sort.kind() == null) yield true;
        yield sortTerm.kind() == sort.kind();
      }
      default -> false;
    };
  }

  private boolean matchTele(@NotNull ImmutableSeq<TermShape> shape, @NotNull ImmutableSeq<Param> tele) {
    return shape.sizeEquals(tele) && shape.allMatchWith(tele, this::matchParam);
  }

  private boolean matchParam(@NotNull TermShape shape, @NotNull Param param) {
    return matchTerm(shape, param.type());
  }

  /**
   * Do `prepare` before matcher, like add the Data to context before matching its cons.
   * This function can be viewed as {@link #captureIfMatches}
   * with a "rollback" feature.
   *
   * @implNote DO NOT call me inside myself.
   */
  private boolean matchInside(@NotNull Runnable prepare, @NotNull BooleanSupplier matcher) {
    captures.fork();
    prepare.run();
    var ok = matcher.getAsBoolean();
    if (ok) captures.merge();
    else captures.discard();
    return ok;
  }

  /**
   * Captures the given {@code var} if the provided {@code matcher} returns true.
   *
   * @see #captureIfMatches(Moment, Object, BiPredicate, Function)
   */
  private boolean captureIfMatches(
    @NotNull MomentId name, @NotNull AnyVar var,
    @NotNull BooleanSupplier matcher
  ) {
    var ok = matcher.getAsBoolean();
    if (ok) captures.put(name, var);
    return ok;
  }

  /***
   * Only add the matched shape to the captures if the matcher returns true.
   * Unlike {@link #matchInside(Runnable, BooleanSupplier)},
   * which may add something to the captures before the match.
   * @see #captureIfMatches(MomentId, AnyVar, BooleanSupplier)
   */
  private <S extends Moment, C> boolean captureIfMatches(
    @NotNull S shape, @NotNull C core,
    @NotNull BiPredicate<S, C> matcher,
    @NotNull Function<C, DefVar<?, ?>> extract
  ) {
    return captureIfMatches(shape.name(), extract.apply(core),
      () -> matcher.test(shape, core));
  }

  private static <S, C> boolean matchMany(
    @NotNull MatchMode mode,
    @NotNull SeqLike<S> shapes,
    @NotNull SeqLike<C> cores,
    @NotNull BiPredicate<S, C> matcher
  ) {
    if (mode == MatchMode.Eq && !shapes.sizeEquals(cores)) return false;
    if (mode == MatchMode.OrderedEq) return shapes.allMatchWith(cores, matcher);
    var remainingShapes = MutableLinkedList.from(shapes);
    for (var core : cores) {
      if (remainingShapes.isEmpty()) return mode == MatchMode.Sub;
      var index = remainingShapes.indexWhere(shape -> matcher.test(shape, core));
      if (index == -1) {
        if (mode != MatchMode.Sub) return false;
      } else {
        remainingShapes.removeAt(index);
      }
    }
    return remainingShapes.isEmpty() || mode == MatchMode.Sup;
  }

  private @NotNull AnyDef resolveCon(@NotNull MomentId data, @NotNull CodeShape.GlobalId conId) {
    if (!(captures.resolve(data) instanceof DefVar<?, ?> defVar)) throw new Panic("Not a data");
    var recog = discovered.getOrThrow(TyckAnyDef.make(defVar.core), () -> new Panic("Not a recognized data"));
    return recog.captures().getOrThrow(conId, () -> new Panic("No such con"));
  }

  public enum MatchMode {
    OrderedEq,
    // fewer shapes match more cores
    Sub,
    // shapes match cores
    Eq,
    // more shapes match less cores
    Sup
  }
}
