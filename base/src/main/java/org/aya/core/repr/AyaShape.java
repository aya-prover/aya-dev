// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.repr;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableLinkedHashMap;
import kala.collection.mutable.MutableMap;
import kala.control.Either;
import kala.control.Option;
import kala.tuple.Tuple;
import kala.tuple.Tuple2;
import org.aya.core.def.Def;
import org.aya.core.def.GenericDef;
import org.aya.core.repr.CodeShape.*;
import org.jetbrains.annotations.NotNull;

import static org.aya.core.repr.CodeShape.CtorShape;
import static org.aya.core.repr.CodeShape.DataShape;
import static org.aya.core.repr.CodeShape.ParamShape.anyLicit;

/**
 * @author kiva
 */
public sealed interface AyaShape {
  @NotNull CodeShape codeShape();

  @NotNull AyaShape NAT_SHAPE = AyaIntShape.INSTANCE;
  @NotNull AyaShape LIST_SHAPE = AyaListShape.INSTANCE;
  @NotNull ImmutableSeq<AyaShape> LITERAL_SHAPES = ImmutableSeq.of(NAT_SHAPE, LIST_SHAPE);

  enum AyaIntShape implements AyaShape {
    INSTANCE;

    public static final @NotNull CodeShape DATA_NAT = new DataShape(ImmutableSeq.empty(), ImmutableSeq.of(
      new CtorShape(CodeShape.MomentId.ZERO, ImmutableSeq.empty()),
      new CtorShape(CodeShape.MomentId.SUC, ImmutableSeq.of(CodeShape.ParamShape.explicit(CodeShape.TermShape.Call.justCall(0))))
    ));

    @Override public @NotNull CodeShape codeShape() {
      return DATA_NAT;
    }
  }

  enum AyaListShape implements AyaShape {
    INSTANCE;

    public static final @NotNull CodeShape DATA_LIST = new DataShape(
      ImmutableSeq.of(anyLicit(new CodeShape.TermShape.Sort(null, 0))),
      ImmutableSeq.of(
        new CtorShape(CodeShape.MomentId.NIL, ImmutableSeq.empty()),
        new CtorShape(CodeShape.MomentId.CONS, ImmutableSeq.of(
          anyLicit(new CodeShape.TermShape.TeleRef(0, 0)),   // A
          anyLicit(new CodeShape.TermShape.Call(0, ImmutableSeq.of(    // List A
            new CodeShape.TermShape.TeleRef(0, 0))))))
      ));

    @Override public @NotNull CodeShape codeShape() {
      return DATA_LIST;
    }
  }

  enum AyaPlusFnShape implements AyaShape {
    INSTANCE;

    private static final @NotNull String NAT = "Nat";
    private static final @NotNull String A = "a";
    private static final @NotNull String B = "b";
    private static final @NotNull String PLUS = "plus";

    public static final @NotNull CodeShape FN_PLUS = new CodeShape.FnShape(
      // _ : Nat -> Nat -> Nat
      ImmutableSeq.of(
        anyLicit(new CodeShape.TermShape.ShapeCall(AyaIntShape.DATA_NAT, ImmutableSeq.empty()).named(NAT)),
        anyLicit(new CodeShape.TermShape.NameCall(NAT, ImmutableSeq.empty()))
      ),
      new CodeShape.TermShape.NameCall(NAT, ImmutableSeq.empty()),
      Either.right(ImmutableSeq.of(
        // | a, 0 => a
        new CodeShape.ClauseShape(ImmutableSeq.of(
          CodeShape.PatShape.Bind.INSTANCE.named(A), new CodeShape.PatShape.ShapedCtor(NAT, CodeShape.MomentId.ZERO, ImmutableSeq.empty())
        ), new CodeShape.TermShape.NameRef(A)),
        // | a, suc b => _ (suc a) b
        new CodeShape.ClauseShape(ImmutableSeq.of(
          PatShape.Bind.INSTANCE.named(A), new PatShape.ShapedCtor(NAT, MomentId.SUC, ImmutableSeq.of(PatShape.Bind.INSTANCE.named(B)))
        ), new TermShape.NameCall(PLUS, ImmutableSeq.of(new TermShape.CtorCall(NAT, MomentId.SUC, ImmutableSeq.of(new TermShape.NameRef(A))), new TermShape.NameRef(B))))
      ))
    ).named(PLUS);

    @Override
    public @NotNull CodeShape codeShape() {
      return FN_PLUS;
    }
  }

  class Factory {
    public @NotNull MutableMap<GenericDef, ShapeRecognition> discovered = MutableLinkedHashMap.of();

    public @NotNull ImmutableSeq<Tuple2<GenericDef, ShapeRecognition>> findImpl(@NotNull AyaShape shape) {
      return discovered.view().map(Tuple::of)
        .filter(t -> t.component2().shape() == shape)
        .toImmutableSeq();
    }

    public @NotNull Option<ShapeRecognition> find(@NotNull Def def) {
      return discovered.getOption(def);
    }

    public void bonjour(@NotNull GenericDef def, @NotNull ShapeRecognition shape) {
      // TODO[literal]: what if a def has multiple shapes?
      discovered.put(def, shape);
    }

    /** Discovery of shaped literals */
    public void bonjour(@NotNull GenericDef def) {
      AyaShape.LITERAL_SHAPES.view()
        .flatMap(shape -> ShapeMatcher.match(shape, def))
        .forEach(shape -> bonjour(def, shape));
    }

    public void importAll(@NotNull Factory other) {
      discovered.putAll(other.discovered);
    }
  }
}
