// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.repr;

import kala.collection.SeqLike;
import kala.collection.immutable.ImmutableMap;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableLinkedList;
import kala.collection.mutable.MutableMap;
import kala.control.Option;
import kala.tuple.Tuple;
import kala.value.MutableValue;
import org.aya.core.def.CtorDef;
import org.aya.core.def.DataDef;
import org.aya.core.def.FnDef;
import org.aya.core.def.GenericDef;
import org.aya.core.pat.Pat;
import org.aya.core.repr.CodeShape.*;
import org.aya.core.term.Callable;
import org.aya.core.term.RefTerm;
import org.aya.core.term.SortTerm;
import org.aya.core.term.Term;
import org.aya.generic.Modifier;
import org.aya.ref.AnyVar;
import org.aya.ref.DefVar;
import org.aya.util.Arg;
import org.aya.util.Pair;
import org.aya.util.RepoLike;
import org.aya.util.error.InternalException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

/**
 * @author kiva
 */
public record ShapeMatcher(
  @NotNull Captures captures,
  @NotNull MutableMap<AnyVar, AnyVar> teleSubst,
  // --------
  @NotNull ImmutableMap<DefVar<?, ?>, ShapeRecognition> discovered
) {

  public record Captures(
    @NotNull MutableMap<MomentId, AnyVar> map,
    @NotNull MutableValue<Captures> future
  ) implements RepoLike<Captures> {
    public static @NotNull Captures create() {
      return new Captures(MutableMap.create(), MutableValue.create());
    }

    public @NotNull ImmutableMap<MomentId, DefVar<?, ?>> extractGlobal() {
      return ImmutableMap.from(map.toImmutableSeq().view()
        .mapNotNull(x -> switch (new Pair<>(x.component1(), x.component2())) {
          case Pair(GlobalId gid, DefVar<?, ?> dv) -> Tuple.of(gid, dv);
          default -> null;
        }));
    }

    @Override public void setDownstream(@Nullable Captures downstream) {
      future.set(downstream);
    }

    public void fork() {
      RepoLike.super.fork(new Captures(MutableMap.from(map), MutableValue.create()));
    }

    public void discard() {
      // closed with unmerged changes
      RepoLike.super.merge();
    }

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
      return choose().getOrThrow(id, () -> new InternalException("Invalid moment id " + id));
    }

    public void put(@NotNull MomentId id, @NotNull AnyVar var) {
      choose().put(id, var);
    }
  }

  public ShapeMatcher() {
    this(Captures.create(), MutableMap.create(), ImmutableMap.empty());
  }

  public ShapeMatcher(@NotNull ImmutableMap<DefVar<?, ?>, ShapeRecognition> discovered) {
    this(Captures.create(), MutableMap.create(), discovered);
  }

  public Option<ShapeRecognition> match(@NotNull AyaShape shape, @NotNull GenericDef def) {
    if (matchDecl(new MatchDecl(shape.codeShape(), def))) {
      return Option.some(new ShapeRecognition(shape, captures.extractGlobal()));
    }

    return Option.none();
  }

  record MatchDecl(@NotNull CodeShape shape, @NotNull GenericDef def) {
  }

  private boolean matchDecl(@NotNull MatchDecl params) {
    return switch (params) {
      case MatchDecl(DataShape dataShape, DataDef data) -> matchData(dataShape, data);
      case MatchDecl(FnShape fnShape, FnDef fn) -> matchFn(fnShape, fn);
      default -> false;
    };
  }

  private boolean matchFn(@NotNull FnShape shape, @NotNull FnDef def) {
    // match signature
    var teleResult = matchTele(shape.tele(), def.telescope)
      && matchTerm(shape.result(), def.result);
    if (!teleResult) return false;

    // match body
    return shape.body().fold(termShape -> {
      if (!def.body.isLeft()) return false;
      var term = def.body.getLeftValue();
      return matchInside(() -> captures.put(shape.name(), def.ref), () -> matchTerm(termShape, term));
    }, clauseShapes -> {
      if (!def.body.isRight()) return false;
      var clauses = def.body.getRightValue();
      var mode = def.modifiers.contains(Modifier.Overlap) ? MatchMode.Sub : MatchMode.Eq;
      return matchInside(() -> captures.put(shape.name(), def.ref), () ->
        matchMany(mode, clauseShapes, clauses, this::matchClause));
    });
  }

  private boolean matchClause(@NotNull ClauseShape shape, @NotNull Term.Matching clause) {
    // match pats
    var patsResult = matchMany(MatchMode.OrderedEq, shape.pats(), clause.patterns(),
      (ps, ap) -> matchPat(new MatchPat(ps, ap.term())));
    if (!patsResult) return false;
    return matchTerm(shape.body(), clause.body());
  }

  record MatchPat(@NotNull PatShape shape, @NotNull Pat pat) {}

  private boolean matchPat(@NotNull MatchPat matchPat) {
    if (matchPat.shape == PatShape.Any.INSTANCE) return true;
    return switch (matchPat) {
      case MatchPat(PatShape.Bind(var name), Pat.Bind ignored) -> {
        captures.put(name, ignored.bind());
        yield true;
      }
      case MatchPat(PatShape.CtorLike ctorLike, Pat.Ctor ctor) -> {
        boolean matched = true;

        if (ctorLike instanceof PatShape.ShapedCtor shapedCtor) {
          var data = captures.resolve(shapedCtor.dataId());
          if (!(data instanceof DefVar<?, ?> defVar)) {
            throw new InternalException("Invalid name: " + shapedCtor.dataId());
          }

          var recognition = discovered.getOrThrow(defVar, () -> new InternalException("Not a shaped data"));
          var realShapedCtor = recognition.captures().getOrThrow(shapedCtor.ctorId(), () ->
            new InternalException("Invalid moment id: " + shapedCtor.ctorId() + " in recognition" + recognition));

          matched = realShapedCtor == ctor.ref();
        }

        if (!matched) yield false;

        // TODO: licit
        // We don't use `matchInside` here, because the context doesn't need to reset.
        yield matchMany(MatchMode.OrderedEq, ctorLike.innerPats(), ctor.params().view().map(Arg::term),
          (ps, pt) -> matchPat(new MatchPat(ps, pt)));
      }
      default -> false;
    };
  }

  private boolean matchData(@NotNull DataShape shape, @NotNull DataDef data) {
    if (!matchTele(shape.tele(), data.telescope)) return false;
    return matchInside(() -> captures.put(shape.name(), data.ref),
      () -> matchMany(MatchMode.Eq, shape.ctors(), data.body,
        (s, c) -> captureIfMatches(s, c, this::matchCtor, CtorDef::ref)));
  }

  private boolean matchCtor(@NotNull CtorShape shape, @NotNull CtorDef ctor) {
    if (ctor.pats.isNotEmpty()) throw new InternalException("Don't try to do this, ask @ice1000 why");
    return matchTele(shape.tele(), ctor.selfTele);
  }

  private boolean matchTerm(@NotNull TermShape shape, @NotNull Term term) {
    @Nullable AnyVar result = null;

    if (shape instanceof TermShape.Any) return true;
    if (shape instanceof TermShape.NameCall call && call.args().isEmpty() && term instanceof RefTerm ref) {
      var success = captures.resolve(call.name()) == ref.var();
      if (!success) return false;
      result = ref.var();
    }

    if (shape instanceof TermShape.Callable call && term instanceof Callable callable) {
      boolean success = switch (call) {
        case TermShape.NameCall nameCall -> captures.resolve(nameCall.name()) == callable.ref();
        case TermShape.ShapeCall shapeCall -> {
          if (callable.ref() instanceof DefVar<?, ?> defVar) {
            yield captureIfMatches(shapeCall.name(), defVar, () ->
              discovered.getOption(defVar).map(x -> x.shape().codeShape()).getOrNull() == shapeCall.shape());
          }

          yield false;
        }
        case TermShape.CtorCall ctorCall -> resolveCtor(ctorCall.dataId(), ctorCall.ctorId()) == callable.ref();
      };

      if (!success) return false;

      success = matchMany(MatchMode.OrderedEq, call.args(), callable.args(),
        (l, r) -> matchTerm(l, r.term()));

      if (!success) return false;
      result = callable.ref();
    }

    if (shape instanceof TermShape.Sort sort && term instanceof SortTerm sortTerm) {
      // kind is null -> any sort
      if (sort.kind() == null) return true;

      // TODO[hoshino]: match kind, but I don't know how to do.
      throw new UnsupportedOperationException("TODO");
    }

    return result != null;
  }

  private boolean matchTele(@NotNull ImmutableSeq<ParamShape> shape, @NotNull ImmutableSeq<Term.Param> tele) {
    return shape.sizeEquals(tele) && shape.allMatchWith(tele, this::matchParam);
  }

  private boolean matchParam(@NotNull ParamShape shape, @NotNull Term.Param param) {
    return switch (shape) {
      case ParamShape.Any any -> true;
      case ParamShape.Licit licit -> {
        if (!matchLicit(licit.kind(), param.explicit())) yield false;
        yield captureIfMatches(licit.name(), param.ref(),
          () -> matchTerm(licit.type(), param.type()));
      }
    };
  }

  private boolean matchLicit(@NotNull ParamShape.Licit.Kind xlicit, boolean isExplicit) {
    return xlicit == ParamShape.Licit.Kind.Any
      || (xlicit == ParamShape.Licit.Kind.Ex) == isExplicit;
  }

  /**
   * Do `prepare` before matcher, like add the Data to context before matching its ctors.
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
   * @see #captureIfMatches(Moment, Object, BiFunction, Function)
   */
  private boolean captureIfMatches(@NotNull MomentId name, @NotNull AnyVar var,
                                   @NotNull BooleanSupplier matcher) {
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
  private <S extends CodeShape.Moment, C> boolean captureIfMatches(
    @NotNull S shape, @NotNull C core,
    @NotNull BiFunction<S, C, Boolean> matcher,
    @NotNull Function<C, DefVar<?, ?>> extract
  ) {
    return captureIfMatches(shape.name(), extract.apply(core),
      () -> matcher.apply(shape, core));
  }

  private static <S, C> boolean matchMany(
    @NotNull MatchMode mode,
    @NotNull SeqLike<S> shapes,
    @NotNull SeqLike<C> cores,
    @NotNull BiFunction<S, C, Boolean> matcher) {
    if (mode == MatchMode.Eq && !shapes.sizeEquals(cores)) return false;
    if (mode == MatchMode.OrderedEq) return shapes.allMatchWith(cores, matcher::apply);
    var remainingShapes = MutableLinkedList.from(shapes);
    for (var core : cores) {
      if (remainingShapes.isEmpty()) return mode == MatchMode.Sub;
      var index = remainingShapes.indexWhere(shape -> matcher.apply(shape, core));
      if (index == -1) {
        if (mode != MatchMode.Sub) return false;
      } else {
        remainingShapes.removeAt(index);
      }
    }
    return remainingShapes.isEmpty() || mode == MatchMode.Sup;
  }

  private @NotNull DefVar<?, ?> resolveCtor(@NotNull MomentId data, @NotNull CodeShape.MomentId ctorId) {
    var someVar = captures.resolve(data);
    if (!(someVar instanceof DefVar<?, ?> defVar)) {
      throw new InternalException("Not a data");
    }

    var recog = discovered.getOrThrow(defVar,
      () -> new InternalException("Not a recognized data"));

    return recog.captures().getOrThrow(ctorId,
      () -> new InternalException("No such ctor"));
  }

  public enum MatchMode {
    OrderedEq,
    // less shapes match more cores
    Sub,
    // shapes match cores
    Eq,
    // more shapes match less cores
    Sup
  }
}
