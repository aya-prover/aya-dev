// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.repr;

import kala.collection.SeqLike;
import kala.collection.immutable.ImmutableMap;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableLinkedList;
import kala.collection.mutable.MutableList;
import kala.collection.mutable.MutableMap;
import kala.control.Option;
import org.aya.concrete.stmt.decl.TeleDecl;
import org.aya.core.def.*;
import org.aya.core.pat.Pat;
import org.aya.core.term.Callable;
import org.aya.core.term.RefTerm;
import org.aya.core.term.SortTerm;
import org.aya.core.term.Term;
import org.aya.generic.Modifier;
import org.aya.ref.AnyVar;
import org.aya.ref.DefVar;
import org.aya.util.Arg;
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
  @NotNull MutableLinkedList<DefVar<? extends Def, ? extends TeleDecl<?>>> def,
  @NotNull MutableMap<CodeShape.MomentId, DefVar<?, ?>> captures,
  @NotNull MutableMap<AnyVar, AnyVar> teleSubst,
  // --------
  @NotNull ImmutableMap<DefVar<?, ?>, ShapeRecognition> discovered,
  @NotNull MutableList<String> names,
  @NotNull MutableMap<String, AnyVar> resolved
) {

  public ShapeMatcher() {
    this(MutableLinkedList.create(), MutableMap.create(), MutableMap.create(), ImmutableMap.empty(), MutableList.create(), MutableMap.create());
  }

  public ShapeMatcher(@NotNull ImmutableMap<DefVar<?, ?>, ShapeRecognition> discovered) {
    this(MutableLinkedList.create(), MutableMap.create(), MutableMap.create(), discovered, MutableList.create(), MutableMap.create());
  }

  public Option<ShapeRecognition> match(@NotNull AyaShape shape, @NotNull GenericDef def) {
    if (matchDecl(shape.codeShape(), def)) {
      return Option.some(new ShapeRecognition(shape, ImmutableMap.from(captures)));
    }

    return Option.none();
  }

  private boolean matchDecl(@NotNull CodeShape shape, @NotNull GenericDef def) {
    if (shape instanceof CodeShape.Named named) {
      names.append(named.name());
      return matchDecl(named.shape(), def);
    }

    if (shape instanceof CodeShape.DataShape dataShape && def instanceof DataDef data) {
      return matchData(dataShape, data);
    }

    if (shape instanceof CodeShape.FnShape fnShape && def instanceof FnDef fn) {
      return matchFn(fnShape, fn);
    }

    return false;
  }

  private boolean matchFn(@NotNull CodeShape.FnShape shape, @NotNull FnDef def) {
    var names = acquireName();

    // match signature
    var teleResult = matchTele(shape.tele(), def.telescope)
      && matchTerm(shape.result(), def.result);
    if (!teleResult) return false;

    // match body
    return shape.body().fold(
      termShape -> {
        if (!def.body.isLeft()) return false;
        var term = def.body.getLeftValue();
        return matchInside(def.ref, names, () -> matchTerm(termShape, term));
      },
      clauseShapes -> {
        if (!def.body.isRight()) return false;
        var clauses = def.body.getRightValue();
        var mode = def.modifiers.contains(Modifier.Overlap) ? MatchMode.Sub : MatchMode.Eq;
        return matchMany(mode, clauseShapes, clauses,
          (cs, m) -> matchInside(def.ref, names, () -> matchClause(cs, m)));
      }
    );
  }

  private boolean matchClause(@NotNull CodeShape.ClauseShape shape, @NotNull Term.Matching clause) {
    // match pats
    var patsResult = matchMany(MatchMode.OrderedEq, shape.pats(), clause.patterns(), (ps, ap) -> matchPat(ps, ap.term()));
    if (!patsResult) return false;
    return matchTerm(shape.body(), clause.body());
  }

  private boolean matchPat(@NotNull PatShape shape, @NotNull Pat pat) {
    if (shape instanceof PatShape.Named named) {
      names.append(named.name());
      return matchPat(named.pat(), pat);
    }

    var names = acquireName();

    if (shape == PatShape.Any.INSTANCE) return true;
    if (shape instanceof PatShape.CtorLike ctorLike && pat instanceof Pat.Ctor ctor) {
      boolean matched = true;

      if (ctorLike instanceof PatShape.ShapedCtor shapedCtor) {
        var data = resolved.getOrNull(shapedCtor.name());
        if (!(data instanceof DefVar<?, ?> defVar)) {
          throw new InternalException("Invalid name: " + shapedCtor.name());
        }

        var recognition = discovered.getOrNull(defVar);
        if (recognition == null) {
          throw new InternalException("Not a shaped data");
        }

        var realShapedCtor = recognition.captures().getOrNull(shapedCtor.id());
        if (realShapedCtor == null) {
          throw new InternalException("Invalid moment id: " + shapedCtor.id() + " in recognition" + recognition);
        }

        matched = realShapedCtor == ctor.ref();
      }

      if (!matched) return false;

      bind(names, ctor.ref());

      // TODO: licit
      // We don't use `matchInside` here, because the context doesn't need to reset.
      return matchMany(MatchMode.OrderedEq, ctorLike.innerPats(), ctor.params().view().map(Arg::term), this::matchPat);
    }

    if (shape == PatShape.Bind.INSTANCE && pat instanceof Pat.Bind bind) {
      bind(names, bind.bind());
      return true;
    }

    return false;
  }

  private boolean matchData(@NotNull CodeShape.DataShape shape, @NotNull DataDef data) {
    var names = acquireName();

    return matchTele(shape.tele(), data.telescope)
      && matchInside(data.ref, names, () -> matchMany(MatchMode.Eq, shape.ctors(), data.body,
      (s, c) -> captured(s, c, this::matchCtor, CtorDef::ref)));
  }

  private boolean matchCtor(@NotNull CodeShape.CtorShape shape, @NotNull CtorDef ctor) {
    if (ctor.pats.isNotEmpty()) ctor.dataRef.core.telescope.forEachWith(ctor.ownerTele,
      (t1, t2) -> teleSubst.put(t1.ref(), t2.ref()));
    return matchTele(shape.tele(), ctor.selfTele);
  }

  private boolean matchTerm(@NotNull TermShape shape, @NotNull Term term) {
    if (shape instanceof TermShape.Named named) {
      names.append(named.name());
      return matchTerm(named.shape(), term);
    }

    var names = acquireName();
    @Nullable AnyVar result = null;

    if (shape instanceof TermShape.Any) return true;
    if (shape instanceof TermShape.NameCall call && call.args().isEmpty() && term instanceof RefTerm ref) {
      var success = resolve(call.name()) == ref.var();
      if (!success) return false;
      result = ref.var();
    }

    if (shape instanceof TermShape.Callable call && term instanceof Callable callable) {
      boolean success = switch (call) {
        case TermShape.NameCall nameCall -> resolve(nameCall.name()) == callable.ref();
        case TermShape.ShapeCall shapeCall -> {
          if (callable.ref() instanceof DefVar<?, ?> defVar) {
            var success0 = discovered.getOption(defVar).map(x -> x.shape().codeShape()).getOrNull() == shapeCall.shape();
            if (success0) {
              captures.put(shapeCall.id(), defVar);
            }

            yield success0;
          }

          yield false;
        }
        case TermShape.CtorCall ctorCall -> resolveCtor(ctorCall.dataRef(), ctorCall.ctorId()) == callable.ref();
      };

      if (!success) return false;

      success = matchMany(MatchMode.OrderedEq, call.args(), callable.args(),
        (l, r) -> matchTerm(l, r.term()));

      if (!success) return false;
      result = callable.ref();
    }

    if (shape instanceof TermShape.TeleRef ref && term instanceof RefTerm refTerm) {
      var superLevel = def.getOrNull(ref.superLevel());
      if (superLevel == null) return false;
      var tele = Def.defTele(superLevel).getOrNull(ref.nth());
      if (tele == null) return false;
      var teleVar = teleSubst.getOrNull(tele.ref());
      return teleVar == refTerm.var() || tele.ref() == refTerm.var();
    }

    if (shape instanceof TermShape.Sort sort && term instanceof SortTerm sortTerm) {
      // kind is null -> any sort
      if (sort.kind() == null) return true;

      // TODO[hoshino]: match kind, but I don't know how to do.
      throw new UnsupportedOperationException("TODO");
    }

    if (result != null) {
      bind(names, result);
      return true;
    }

    return false;
  }

  private boolean matchTele(@NotNull ImmutableSeq<ParamShape> shape, @NotNull ImmutableSeq<Term.Param> tele) {
    var shapes = shape.view();
    var params = tele.view();
    while (shapes.isNotEmpty() && params.isNotEmpty()) {
      var s = shapes.first();
      var c = params.first();
      if (!matchParam(s, c)) return false;
      shapes = shapes.drop(1);
      params = params.drop(1);
    }
    if (shapes.isNotEmpty()) {
      // implies params.isEmpty(), matching all optional shapes
      shapes = shapes.filterNot(ParamShape.Optional.class::isInstance);
    }
    return shapes.sizeEquals(params);
  }

  private boolean matchParam(@NotNull ParamShape shape, @NotNull Term.Param param) {
    if (shape instanceof ParamShape.Named named) {
      names.append(named.name());
      return matchParam(named.shape(), param);
    }

    var names = acquireName();
    bind(names, param.ref());

    return switch (shape) {
      case ParamShape.Any any -> true;
      case ParamShape.Optional opt -> matchParam(opt.param(), param);
      case ParamShape.Licit licit -> {
        if (!matchLicit(licit.kind(), param.explicit())) yield false;
        yield matchTerm(licit.type(), param.type());
      }
      default -> false;
    };
  }

  private boolean matchLicit(@NotNull ParamShape.Licit.Kind xlicit, boolean isExplicit) {
    return xlicit == ParamShape.Licit.Kind.Any
      || (xlicit == ParamShape.Licit.Kind.Ex) == isExplicit;
  }

  private boolean matchInside(@NotNull DefVar<? extends Def, ? extends TeleDecl<?>> defVar, @NotNull ImmutableSeq<String> names, @NotNull BooleanSupplier matcher) {
    var snapshot = ImmutableMap.from(resolved);

    bind(names, defVar);

    def.push(defVar);
    var result = matcher.getAsBoolean();
    def.pop();

    resolved.clear();
    resolved.putAll(snapshot);

    return result;
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

  private <S extends CodeShape.Moment, C> boolean captured(
    @NotNull S shape, @NotNull C core,
    @NotNull BiFunction<S, C, Boolean> matcher,
    @NotNull Function<C, DefVar<?, ?>> extract
  ) {
    var matched = matcher.apply(shape, core);
    if (matched) captures.put(shape.name(), extract.apply(core));
    return matched;
  }

  private void bind(@NotNull ImmutableSeq<String> names, @NotNull AnyVar someVar) {
    names.forEach(name -> resolved.put(name, someVar));
  }

  private @NotNull ImmutableSeq<String> acquireName() {
    var result = names.toImmutableSeq();
    names.clear();
    return result;
  }

  private @NotNull AnyVar resolve(@NotNull String name) {
    var resolved = this.resolved.getOrNull(name);
    if (resolved == null) {
      throw new InternalException("Invalid name: " + name);
    }

    return resolved;
  }

  private @NotNull DefVar<?, ?> resolveCtor(@NotNull String data, @NotNull CodeShape.MomentId ctorId) {
    var someVar = resolve(data);
    if (!(someVar instanceof DefVar<?, ?> defVar)) {
      throw new InternalException("Not a data");
    }

    var recog = discovered.getOrNull(defVar);
    if (recog == null) {
      throw new InternalException("Not a recognized data");
    }

    var ctor = recog.captures().getOrNull(ctorId);
    if (ctor == null) {
      throw new InternalException("No such ctor");
    }

    return ctor;
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
