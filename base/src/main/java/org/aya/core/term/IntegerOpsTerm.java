// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

import kala.collection.immutable.ImmutableSeq;
import org.aya.concrete.stmt.decl.TeleDecl;
import org.aya.core.def.CtorDef;
import org.aya.core.def.Def;
import org.aya.core.def.FnDef;
import org.aya.core.pat.Pat;
import org.aya.core.repr.AyaShape;
import org.aya.core.repr.ShapeRecognition;
import org.aya.generic.Shaped;
import org.aya.ref.DefVar;
import org.aya.util.Arg;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.util.function.UnaryOperator;

public record IntegerOpsTerm(
  @Override @NotNull DefVar<? extends Def, ? extends TeleDecl<?>> ref,
  @NotNull Kind kind,
  @NotNull ShapeRecognition paramRecog,
  @NotNull DataCall paramType
) implements Shaped.Fn<Term>, Term {
  public IntegerOpsTerm {
    assert paramRecog.shape() == AyaShape.NAT_SHAPE;

    switch (kind) {
      case Zero, Succ -> {
        assert ref.core instanceof CtorDef || ref.concrete instanceof TeleDecl.DataCtor;
      }
      case Add, SubTrunc -> {
        assert ref.core instanceof FnDef || ref.concrete instanceof TeleDecl.FnDecl;
      }
    }
  }

  @Override public @NotNull Term type() {
    assert ref.core != null;
    return PiTerm.make(ref.core.telescope(), ref.core.result());
  }

  private @NotNull IntegerOpsTerm update(@NotNull DataCall paramType) {
    return paramType == this.paramType ? this : new IntegerOpsTerm(
      ref, kind, paramRecog, paramType
    );
  }

  @Override
  public @NotNull Term descent(@NotNull UnaryOperator<Term> f, @NotNull UnaryOperator<Pat> g) {
    return update((DataCall) f.apply(paramType));
  }

  public enum Kind implements Serializable {
    Zero, Succ,
    Add, SubTrunc
  }

  private @NotNull IntegerTerm from(int repr) {
    return new IntegerTerm(repr, paramRecog, paramType);
  }

  @Override public @Nullable Term apply(@NotNull ImmutableSeq<Arg<Term>> args) {
    return switch (kind) {
      case Zero -> {
        assert args.isEmpty();
        yield from(0);
      }
      case Succ -> {
        assert args.sizeEquals(1);
        var arg = args.get(0).term();
        if (arg instanceof IntegerTerm it) {
          yield from(it.repr() + 1);
        }

        yield null;
      }
      case Add -> {
        assert args.sizeEquals(2);
        var a = args.get(0).term();
        var b = args.get(1).term();
        if (a instanceof IntegerTerm ita && b instanceof IntegerTerm itb) {
          yield from(ita.repr() + itb.repr());
        }

        yield null;
      }
      case SubTrunc -> {
        assert args.sizeEquals(2);
        var a = args.get(0).term();
        var b = args.get(1).term();
        if (a instanceof IntegerTerm ita && b instanceof IntegerTerm itb) {
          yield from(Math.max(ita.repr() - itb.repr(), 0));
        }

        yield null;
      }
    };
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof IntegerOpsTerm term) {
      return this.ref == term.ref && this.kind == term.kind;
    }

    return false;
  }
}
