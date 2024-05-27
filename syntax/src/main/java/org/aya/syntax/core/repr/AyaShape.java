// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.repr;

import kala.collection.immutable.ImmutableSeq;
import org.aya.generic.stmt.Shaped;
import org.aya.syntax.core.def.AnyDef;
import org.aya.syntax.core.def.ConDefLike;
import org.aya.syntax.core.def.FnDefLike;
import org.aya.syntax.core.repr.CodeShape.*;
import org.aya.syntax.core.term.call.DataCall;
import org.aya.syntax.core.term.repr.IntegerOps;
import org.aya.syntax.core.term.repr.IntegerTerm;
import org.aya.syntax.core.term.repr.ListOps;
import org.aya.syntax.core.term.repr.ListTerm;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static org.aya.syntax.core.repr.CodeShape.GlobalId.SUC;
import static org.aya.syntax.core.repr.CodeShape.GlobalId.ZERO;
import static org.aya.syntax.core.repr.CodeShape.LocalId.*;

/**
 * @author kiva, ice1000
 */
public enum AyaShape {
  NAT_SHAPE {
    public static final @NotNull CodeShape DATA_NAT = new DataShape(
      DATA,
      ImmutableSeq.empty(), ImmutableSeq.of(
      new ConShape(ZERO, ImmutableSeq.empty()),
      new ConShape(SUC, ImmutableSeq.of(TermShape.NameCall.of(DATA)))
    ));

    @Override public @NotNull CodeShape codeShape() { return DATA_NAT; }
  },
  LIST_SHAPE {
    public static final @NotNull CodeShape DATA_LIST = new DataShape(
      DATA,
      ImmutableSeq.of(new TermShape.Sort(null, 0)),
      ImmutableSeq.of(
        new ConShape(GlobalId.NIL, ImmutableSeq.empty()),
        new ConShape(GlobalId.CONS, ImmutableSeq.of(
          new TermShape.DeBruijn(0),
          TermShape.NameCall.of(DATA, new TermShape.DeBruijn(1))
        )) // List A
      ));

    @Override public @NotNull CodeShape codeShape() { return DATA_LIST; }
  },
  PLUS_LEFT_SHAPE {
    public static final @NotNull FnShape FN_PLUS = CodeShape.binop(NAT_SHAPE.codeShape(),
      // | a, 0 => a
      new ClauseShape(ImmutableSeq.of(
        PatShape.Basic.Bind, PatShape.ShapedCon.of(TYPE, ZERO)
      ), new TermShape.DeBruijn(0)),
      // | a, suc b => suc (_ a b)
      new ClauseShape(ImmutableSeq.of(
        PatShape.Basic.Bind, PatShape.ShapedCon.of(TYPE, SUC,
          PatShape.Basic.Bind)
      ), TermShape.ConCall.of(TYPE, SUC, TermShape.NameCall.of(FUNC,
        new TermShape.DeBruijn(1),
        new TermShape.DeBruijn(0)
      )))
    );

    @Override public @NotNull FnShape codeShape() { return FN_PLUS; }
  },
  PLUS_RIGHT_SHAPE {
    public static final @NotNull CodeShape FN_PLUS = CodeShape.binop(NAT_SHAPE.codeShape(),
      // | 0, b => b
      new ClauseShape(ImmutableSeq.of(
        PatShape.ShapedCon.of(TYPE, ZERO), PatShape.Basic.Bind
      ), new TermShape.DeBruijn(0)),
      // | suc a, b => _ a (suc b)
      new ClauseShape(ImmutableSeq.of(
        PatShape.ShapedCon.of(TYPE, SUC, PatShape.Basic.Bind),
        PatShape.Basic.Bind
      ), TermShape.ConCall.of(TYPE, SUC, TermShape.NameCall.of(FUNC,
        new TermShape.DeBruijn(1),
        new TermShape.DeBruijn(0)
      ))));

    @Override public @NotNull CodeShape codeShape() { return FN_PLUS; }
  },
  MINUS_SHAPE {
    public static final @NotNull CodeShape FN_MINUS = CodeShape.binop(NAT_SHAPE.codeShape(),
      // n - 0 => n
      new ClauseShape(ImmutableSeq.of(
        PatShape.Basic.Bind, PatShape.ShapedCon.of(TYPE, ZERO)
      ), new TermShape.DeBruijn(0)),
      // 0 - suc m = 0
      new ClauseShape(ImmutableSeq.of(
        PatShape.ShapedCon.of(TYPE, ZERO),
        PatShape.ShapedCon.of(TYPE, SUC, PatShape.Basic.Bind)
      ), TermShape.ConCall.of(TYPE, ZERO)),
      // suc n - suc m = n - m
      new ClauseShape(ImmutableSeq.of(
        PatShape.ShapedCon.of(TYPE, SUC, PatShape.Basic.Bind),
        PatShape.ShapedCon.of(TYPE, SUC, PatShape.Basic.Bind)
      ), TermShape.NameCall.of(FUNC,
        new TermShape.DeBruijn(1),
        new TermShape.DeBruijn(0)
      )));

    @Override public @NotNull CodeShape codeShape() { return FN_MINUS; }
  };

  public @NotNull abstract CodeShape codeShape();

  public static Shaped.Applicable<ConDefLike> ofCon(
    @NotNull ConDefLike ref,
    @NotNull ShapeRecognition paramRecog,
    @NotNull DataCall paramType
  ) {
    return switch (paramRecog.shape()) {
      case NAT_SHAPE -> new IntegerOps.ConRule(ref, new IntegerTerm(0, paramRecog, paramType));
      case LIST_SHAPE -> new ListOps.ConRule(ref,
        new ListTerm(ImmutableSeq.empty(), paramRecog, paramType));
      default -> null;
    };
  }

  public static @Nullable Shaped.Applicable<FnDefLike>
  ofFn(FnDefLike ref, @NotNull AyaShape shape) {
    return switch (shape) {
      case PLUS_LEFT_SHAPE, PLUS_RIGHT_SHAPE -> new IntegerOps.FnRule(ref, IntegerOps.FnRule.Kind.Add);
      case MINUS_SHAPE -> new IntegerOps.FnRule(ref, IntegerOps.FnRule.Kind.SubTrunc);
      default -> null;
    };
  }

  public record FindImpl(@NotNull AnyDef def, @NotNull ShapeRecognition recog) { }
}
