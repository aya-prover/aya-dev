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
  @NotNull ImmutableMap<GenericDef, ShapeRecognition> discovered,
  @NotNull MutableList<String> names,
  @NotNull MutableMap<String, AnyVar> resolved
) {
  public ShapeMatcher() {
    this(MutableLinkedList.create(), MutableMap.create(), MutableMap.create(), ImmutableMap.empty(), MutableList.create(), MutableMap.create());
  }

  public static Option<ShapeRecognition> match(@NotNull AyaShape shape, @NotNull GenericDef def) {
    var matcher = new ShapeMatcher();
    var success = matcher.matchDecl(shape.codeShape(), def);

    if (success) {
      return Option.some(new ShapeRecognition(shape, matcher.captures.toImmutableMap()));
    }

    return Option.none();
  }

  private boolean matchDecl(@NotNull CodeShape shape, @NotNull GenericDef def) {
    if (shape instanceof CodeShape.Named named) {
      names.append(named.name());
      return matchDecl(shape, def);
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
    // match signature
    var teleResult = matchTele(shape.tele(), def.telescope)
      && matchTerm(shape.result(), def.result);
    if (!teleResult) return false;

    // match body
    return shape.body().fold(
      termShape -> {
        if (!def.body.isLeft()) return false;
        var term = def.body.getLeftValue();
        return matchTerm(termShape, term);
      },
      clauseShapes -> {
        if (!def.body.isRight()) return false;
        var clauses = def.body.getRightValue();
        return matchMany(false, clauseShapes, clauses, (cs, m) ->
          // inside multiple times in order to reset the state
          matchInside(def.ref, () -> matchClause(cs, m))
        );
      }
    );
  }

  private boolean matchClause(@NotNull CodeShape.ClauseShape shape, @NotNull Term.Matching clause) {
    // match pats
    var patsResult = matchMany(true, shape.pats(), clause.patterns(), (ps, ap) -> matchPat(ps, ap.term()));
    if (!patsResult) return false;
    return matchTerm(shape.body(), clause.body());
  }

  private boolean matchPat(@NotNull CodeShape.PatShape shape, @NotNull Pat pat) {
    if (shape == CodeShape.PatShape.Any.INSTANCE) return true;
    if (shape instanceof CodeShape.PatShape.Named named) {
      names.append(named.name());
      return matchPat(named.pat(), pat);
    }

    if (shape instanceof CodeShape.PatShape.ShapedCtor shapedCtor && pat instanceof Pat.Ctor ctor) {
      var data = resolved.getOrNull(shapedCtor.name());
      if (!(data instanceof DefVar<?, ?> defVar && defVar.core instanceof DataDef dataDef)) {
        throw new InternalException("Invalid name: " + shapedCtor.name());
      }

      var recognition = discovered.getOrNull(dataDef);
      if (recognition == null) {
        throw new InternalException("Not a shaped data");
      }

      var realShapedCtor = recognition.captures().getOrNull(shapedCtor.id());
      if (realShapedCtor == null) {
        throw new InternalException("Invalid moment id: " + shapedCtor.id() + " in recognition" + recognition);
      }

      if (realShapedCtor == ctor.ref()) {
        // resolve inner
        return matchInside(ctor.ref(), () ->
          // TODO: licit
          matchMany(true, shapedCtor.innerPats(), ctor.params().view().map(Arg::term), this::matchPat));
      }
    }

    if (shape instanceof CodeShape.PatShape.Ctor shapedCtor && pat instanceof Pat.Ctor ctor) {
      // TODO: fix duplicated
      return matchInside(ctor.ref(), () ->
        // TODO: licit
        matchMany(true, shapedCtor.innerPats(), ctor.params().view().map(Arg::term), this::matchPat));
    }

    if (shape == CodeShape.PatShape.Bind.INSTANCE && pat instanceof Pat.Bind bind) {
      bind(bind.bind());
      return true;
    }

    return false;
  }

  private boolean matchData(@NotNull CodeShape.DataShape shape, @NotNull DataDef data) {
    return matchTele(shape.tele(), data.telescope)
      && matchInside(data.ref, () -> matchMany(false, shape.ctors(), data.body,
      (s, c) -> captured(s, c, this::matchCtor, CtorDef::ref)));
  }

  private boolean matchCtor(@NotNull CodeShape.CtorShape shape, @NotNull CtorDef ctor) {
    if (ctor.pats.isNotEmpty()) ctor.dataRef.core.telescope.forEachWith(ctor.ownerTele,
      (t1, t2) -> teleSubst.put(t1.ref(), t2.ref()));
    return matchTele(shape.tele(), ctor.selfTele);
  }

  private boolean matchTerm(@NotNull CodeShape.TermShape shape, @NotNull Term term) {
    if (shape instanceof CodeShape.TermShape.Any) return true;
    if (shape instanceof CodeShape.TermShape.Named named) {
      names.append(named.name());
      return matchTerm(named.shape(), term);
    }

    // TODO[hoshino]: For now, we are unable to match `| Ctor (Data {Im} Ex)` and `| Ctor (Data Ex)`
    //                by only one `Shape`, I think the solution is
    //                constructing a Term by Shape and unify them.
    if (shape instanceof CodeShape.TermShape.Call call && term instanceof Callable callable) {
      var superLevel = def.getOrNull(call.superLevel());
      if (superLevel != callable.ref()) return false;                      // implies null check
      // TODO[hoshino]: do we also match implicit arguments when size mismatch?
      return matchMany(true, call.args(), callable.args(),
        (l, r) -> matchTerm(l, r.term()));
    }
    if (shape instanceof CodeShape.TermShape.Callable call && term instanceof Callable callable) {
      boolean success = switch (call) {
        case CodeShape.TermShape.NameCall nameCall -> resolve(nameCall.name()) == callable.ref();
        case CodeShape.TermShape.ShapeCall shapeCall -> callable.ref() instanceof DefVar<?, ?> defVar
          && defVar.core instanceof GenericDef def
          && discovered.getOption(def).map(x -> x.shape().codeShape()).getOrNull() == shapeCall.shape();
        case CodeShape.TermShape.CtorCall ctorCall ->
          resolveCtor(ctorCall.dataRef(), ctorCall.ctorId()) == callable.ref();
      };

      if (!success) return false;

      return matchMany(true, call.args(), callable.args(),
        (l, r) -> matchTerm(l, r.term()));
    }
    if (shape instanceof CodeShape.TermShape.TeleRef ref && term instanceof RefTerm refTerm) {
      var superLevel = def.getOrNull(ref.superLevel());
      if (superLevel == null) return false;
      var tele = Def.defTele(superLevel).getOrNull(ref.nth());
      if (tele == null) return false;
      var teleVar = teleSubst.getOrNull(tele.ref());
      return teleVar == refTerm.var() || tele.ref() == refTerm.var();
    }
    if (shape instanceof CodeShape.TermShape.NameRef ref && term instanceof RefTerm refTerm) {
      return resolve(ref.name()) == refTerm.var();
    }
    if (shape instanceof CodeShape.TermShape.Sort sort && term instanceof SortTerm sortTerm) {
      // kind is null -> any sort
      if (sort.kind() == null) return true;

      // TODO[hoshino]: match kind, but I don't know how to do.
      throw new UnsupportedOperationException("TODO");
    }
    return false;
  }

  private boolean matchTele(@NotNull ImmutableSeq<CodeShape.ParamShape> shape, @NotNull ImmutableSeq<Term.Param> tele) {
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
      shapes = shapes.filterNot(CodeShape.ParamShape.Optional.class::isInstance);
    }
    return shapes.sizeEquals(params);
  }

  private boolean matchParam(@NotNull CodeShape.ParamShape shape, @NotNull Term.Param param) {
    if (shape instanceof CodeShape.ParamShape.Any) return true;
    if (shape instanceof CodeShape.ParamShape.Optional opt) return matchParam(opt.param(), param);
    if (shape instanceof CodeShape.ParamShape.Licit licit) {
      if (!matchLicit(licit.kind(), param.explicit())) return false;
      return matchTerm(licit.type(), param.type());
    }
    return false;
  }

  private boolean matchLicit(@NotNull CodeShape.ParamShape.Licit.Kind xlicit, boolean isExplicit) {
    return xlicit == CodeShape.ParamShape.Licit.Kind.Any
      || (xlicit == CodeShape.ParamShape.Licit.Kind.Ex) == isExplicit;
  }

  private boolean matchInside(@NotNull DefVar<? extends Def, ? extends TeleDecl<?>> defVar, @NotNull BooleanSupplier matcher) {
    var snapshot = resolved.toImmutableMap();

    bind(defVar);
    def.push(defVar);
    var result = matcher.getAsBoolean();
    def.pop();

    resolved.clear();
    resolved.putAll(snapshot);

    return result;
  }

  private static <S, C> boolean matchMany(
    boolean ordered,
    @NotNull SeqLike<S> shapes,
    @NotNull SeqLike<C> cores,
    @NotNull BiFunction<S, C, Boolean> matcher) {
    if (!shapes.sizeEquals(cores)) return false;
    if (ordered) return shapes.allMatchWith(cores, matcher::apply);
    var remainingShapes = MutableLinkedList.from(shapes);
    for (var core : cores) {
      var index = remainingShapes.indexWhere(shape -> matcher.apply(shape, core));
      if (index == -1) return false;
      remainingShapes.removeAt(index);
    }
    return true;
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

  private void bind(@NotNull AnyVar someVar) {
    names.forEach(name -> resolved.put(name, someVar));
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
    if (!(someVar instanceof DefVar<?, ?> defVar && defVar.core instanceof DataDef dataDef)) {
      throw new InternalException("Not a data");
    }

    var recog = discovered.getOrNull(dataDef);
    if (recog == null) {
      throw new InternalException("Not a recognized data");
    }

    var ctor = recog.captures().getOrNull(ctorId);
    if (ctor == null) {
      throw new InternalException("No such ctor");
    }

    return ctor;
  }
}
