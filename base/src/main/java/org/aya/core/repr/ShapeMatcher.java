// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.repr;

import kala.collection.SeqLike;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableLinkedList;
import kala.collection.mutable.MutableMap;
import org.aya.concrete.stmt.Decl;
import org.aya.core.def.CtorDef;
import org.aya.core.def.DataDef;
import org.aya.core.def.Def;
import org.aya.core.def.GenericDef;
import org.aya.core.term.CallTerm;
import org.aya.core.term.RefTerm;
import org.aya.core.term.Term;
import org.aya.ref.DefVar;
import org.aya.ref.Var;
import org.jetbrains.annotations.NotNull;

import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;

/**
 * @author kiva
 */
public record ShapeMatcher(
  @NotNull MutableLinkedList<DefVar<? extends Def, ? extends Decl.Telescopic>> def,
  @NotNull MutableMap<Var, Var> teleSubst
) {
  public static boolean match(@NotNull CodeShape shape, @NotNull GenericDef def) {
    if (shape instanceof CodeShape.DataShape dataShape && def instanceof DataDef data)
      return new ShapeMatcher(MutableLinkedList.create(), MutableMap.create()).matchData(dataShape, data);
    return false;
  }

  private boolean matchData(@NotNull CodeShape.DataShape shape, @NotNull DataDef data) {
    return matchTele(shape.tele(), data.telescope)
      && matchInside(data.ref, () -> matchMany(false, shape.ctors(), data.body, this::matchCtor));
  }

  private boolean matchCtor(@NotNull CodeShape.CtorShape shape, @NotNull CtorDef ctor) {
    if (ctor.pats.isNotEmpty()) {
      ctor.dataRef.core.telescope.zipView(ctor.ownerTele).forEach(t ->
        teleSubst.put(t._1.ref(), t._2.ref()));
    }
    return matchTele(shape.tele(), ctor.selfTele);
  }

  private boolean matchTerm(@NotNull CodeShape.TermShape shape, @NotNull Term term) {
    if (shape instanceof CodeShape.TermShape.Any) return true;
    if (shape instanceof CodeShape.TermShape.Call call && term instanceof CallTerm callTerm) {
      var superLevel = def.getOrNull(call.superLevel());
      return superLevel == callTerm.ref();
    }
    if (shape instanceof CodeShape.TermShape.TeleRef ref && term instanceof RefTerm refTerm) {
      var superLevel = def.getOrNull(ref.superLevel());
      if (superLevel == null) return false;
      var tele = Def.defTele(superLevel).getOrNull(ref.nth());
      if (tele == null) return false;
      var teleVar = teleSubst.getOrNull(tele.ref());
      return teleVar == refTerm.var() || tele.ref() == refTerm.var();
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
      if (licit.explicit() != param.explicit()) return false;
      return matchTerm(licit.type(), param.type());
    }
    return false;
  }

  private boolean matchInside(@NotNull DefVar<? extends Def, ? extends Decl.Telescopic> defVar, @NotNull BooleanSupplier matcher) {
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
    if (ordered) return shapes.zipView(cores).allMatch(tup -> matcher.apply(tup._1, tup._2));
    var remainingShapes = MutableLinkedList.from(shapes);
    for (var core : cores) {
      var index = remainingShapes.indexWhere(shape -> matcher.apply(shape, core));
      if (index == -1) return false;
      remainingShapes.removeAt(index);
    }
    return true;
  }
}
