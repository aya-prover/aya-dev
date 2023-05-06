// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.repr;

import kala.collection.SeqLike;
import kala.collection.immutable.ImmutableMap;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableLinkedList;
import kala.collection.mutable.MutableMap;
import kala.control.Option;
import org.aya.concrete.stmt.decl.TeleDecl;
import org.aya.core.def.CtorDef;
import org.aya.core.def.DataDef;
import org.aya.core.def.Def;
import org.aya.core.def.GenericDef;
import org.aya.core.term.Callable;
import org.aya.core.term.RefTerm;
import org.aya.core.term.SortTerm;
import org.aya.core.term.Term;
import org.aya.ref.AnyVar;
import org.aya.ref.DefVar;
import org.jetbrains.annotations.NotNull;

import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

/**
 * @author kiva
 */
public record ShapeMatcher(
  @NotNull MutableLinkedList<DefVar<? extends Def, ? extends TeleDecl<?>>> def,
  @NotNull MutableMap<CodeShape.MomentId, DefVar<?, ?>> captures,
  @NotNull MutableMap<AnyVar, AnyVar> teleSubst
) {
  public ShapeMatcher() {
    this(MutableLinkedList.create(), MutableMap.create(), MutableMap.create());
  }

  public static Option<ShapeRecognition> match(@NotNull AyaShape shape, @NotNull GenericDef def) {
    var matcher = new ShapeMatcher();
    if (shape.codeShape() instanceof CodeShape.DataShape dataShape && def instanceof DataDef data)
      return matcher.matchData(dataShape, data) ? Option.some(new ShapeRecognition(shape, ImmutableMap.from(matcher.captures))) : Option.none();
    return Option.none();
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
    if (shape instanceof CodeShape.TermShape.TeleRef ref && term instanceof RefTerm refTerm) {
      var superLevel = def.getOrNull(ref.superLevel());
      if (superLevel == null) return false;
      var tele = Def.defTele(superLevel).getOrNull(ref.nth());
      if (tele == null) return false;
      var teleVar = teleSubst.getOrNull(tele.ref());
      return teleVar == refTerm.var() || tele.ref() == refTerm.var();
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
    def.push(defVar);
    var result = matcher.getAsBoolean();
    def.pop();
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
}
