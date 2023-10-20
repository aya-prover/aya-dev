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
import org.jetbrains.annotations.NotNull;

import static org.aya.core.repr.CodeShape.*;
import static org.aya.core.repr.CodeShape.GlobalId.*;
import static org.aya.core.repr.CodeShape.LocalId.LHS;
import static org.aya.core.repr.CodeShape.LocalId.RHS;
import static org.aya.core.repr.ParamShape.anyLicit;

/**
 * @author kiva
 */
public sealed interface AyaShape {
  @NotNull CodeShape codeShape();

  @NotNull AyaShape NAT_SHAPE = AyaIntShape.INSTANCE;
  @NotNull AyaShape LIST_SHAPE = AyaListShape.INSTANCE;
  @NotNull AyaShape PLUS_LEFT_SHAPE = AyaPlusFnLeftShape.INSTANCE;
  @NotNull AyaShape PLUS_RIGHT_SHAPE = AyaPlusFnShape.INSTANCE;
  @NotNull ImmutableSeq<AyaShape> LITERAL_SHAPES = ImmutableSeq.of(NAT_SHAPE, LIST_SHAPE, PLUS_RIGHT_SHAPE);

  enum AyaIntShape implements AyaShape {
    INSTANCE;

    public static final @NotNull CodeShape DATA_NAT = new DataShape(
      NAT,
      ImmutableSeq.empty(), ImmutableSeq.of(
      new CtorShape(ZERO, ImmutableSeq.empty()),
      new CtorShape(SUC, ImmutableSeq.of(ParamShape.explicit(TermShape.NameCall.of(NAT))))
    ));

    @Override public @NotNull CodeShape codeShape() {
      return DATA_NAT;
    }
  }

  enum AyaListShape implements AyaShape {
    INSTANCE;

    public static final @NotNull LocalId A = new LocalId("A");

    public static final @NotNull CodeShape DATA_LIST = new DataShape(
      LIST,
      ImmutableSeq.of(anyLicit(A, new TermShape.Sort(null, 0))),
      ImmutableSeq.of(
        new CtorShape(GlobalId.NIL, ImmutableSeq.empty()),
        new CtorShape(GlobalId.CONS, ImmutableSeq.of(
          anyLicit(TermShape.NameCall.of(A)),
          anyLicit(new TermShape.NameCall(LIST, ImmutableSeq.of(TermShape.NameCall.of(A))))
        )) // List A
      ));

    @Override public @NotNull CodeShape codeShape() {
      return DATA_LIST;
    }
  }

  enum AyaPlusFnShape implements AyaShape {
    INSTANCE;

    public static final @NotNull CodeShape FN_PLUS = new FnShape(
      NAT_ADD,
      // _ : Nat -> Nat -> Nat
      ImmutableSeq.of(
        anyLicit(new TermShape.ShapeCall(NAT, AyaIntShape.DATA_NAT, ImmutableSeq.empty())),
        anyLicit(TermShape.NameCall.of(NAT))
      ),
      TermShape.NameCall.of(NAT),
      Either.right(ImmutableSeq.of(
        // | a, 0 => a
        new ClauseShape(ImmutableSeq.of(
          new PatShape.Bind(LHS), PatShape.ShapedCtor.of(NAT, ZERO)
        ), TermShape.NameCall.of(LHS)),
        // | a, suc b => suc (_ a b)
        new ClauseShape(ImmutableSeq.of(
          new PatShape.Bind(LHS), new PatShape.ShapedCtor(NAT, SUC, ImmutableSeq.of(new PatShape.Bind(RHS)))
        ), new TermShape.CtorCall(NAT, SUC, ImmutableSeq.of(new TermShape.NameCall(NAT_ADD, ImmutableSeq.of(
          TermShape.NameCall.of(LHS),
          TermShape.NameCall.of(RHS)
        )))))
      ))
    );

    @Override
    public @NotNull CodeShape codeShape() {
      return FN_PLUS;
    }
  }

  enum AyaPlusFnLeftShape implements AyaShape {
    INSTANCE;

    public static final @NotNull CodeShape FN_PLUS = new FnShape(
      NAT_ADD,
      // _ : Nat -> Nat -> Nat
      ImmutableSeq.of(
        anyLicit(new TermShape.ShapeCall(GlobalId.NAT, AyaIntShape.DATA_NAT, ImmutableSeq.empty())),
        anyLicit(TermShape.NameCall.of(NAT))
      ),
      TermShape.NameCall.of(NAT),
      Either.right(ImmutableSeq.of(
        // | 0, b => b
        new ClauseShape(ImmutableSeq.of(
          PatShape.ShapedCtor.of(NAT, ZERO), new PatShape.Bind(RHS)
        ), TermShape.NameCall.of(RHS)),
        // | suc a, b => _ a (suc b)
        new ClauseShape(ImmutableSeq.of(
          new PatShape.ShapedCtor(NAT, SUC, ImmutableSeq.of(new PatShape.Bind(LHS))), new PatShape.Bind(RHS)
        ), new TermShape.CtorCall(NAT, SUC, ImmutableSeq.of(new TermShape.NameCall(NAT_ADD, ImmutableSeq.of(
          TermShape.NameCall.of(LHS),
          TermShape.NameCall.of(RHS)
        )))))
      ))
    );

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
        .flatMap(shape -> new ShapeMatcher().match(shape, def))
        .forEach(shape -> bonjour(def, shape));
    }

    public void importAll(@NotNull Factory other) {
      discovered.putAll(other.discovered);
    }
  }
}
