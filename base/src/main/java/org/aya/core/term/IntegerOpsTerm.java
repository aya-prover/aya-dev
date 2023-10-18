// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

import kala.collection.immutable.ImmutableSeq;
import org.aya.concrete.stmt.decl.TeleDecl;
import org.aya.core.def.CtorDef;
import org.aya.core.def.Def;
import org.aya.core.def.FnDef;
import org.aya.core.pat.Pat;
import org.aya.core.repr.CodeShape;
import org.aya.core.repr.ShapeRecognition;
import org.aya.generic.Shaped;
import org.aya.ref.DefVar;
import org.aya.util.Arg;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.util.function.UnaryOperator;

public sealed interface IntegerOpsTerm<Core extends Def, Concrete extends TeleDecl<?>>
  extends Shaped.Appliable<Term, Core, Concrete>, Term {
  @NotNull ShapeRecognition paramRecognition();
  @NotNull DataCall paramType();

  default @NotNull IntegerTerm from(int repr) {
    return new IntegerTerm(repr, paramRecognition(), paramType());
  }

  @Override default @NotNull Term type() {
    var core = ref().core;
    assert core != null;
    return PiTerm.make(core.telescope(), core.result());
  }

  record ConRule(
    @Override @NotNull DefVar<CtorDef, TeleDecl.DataCtor> ref,
    @Override @NotNull ShapeRecognition paramRecognition,
    @Override @NotNull DataCall paramType
  ) implements IntegerOpsTerm<CtorDef, TeleDecl.DataCtor> {
    public boolean isZero() {
      return paramRecognition.captures().get(CodeShape.MomentId.ZERO) == ref;
    }

    @Override
    public @Nullable Term apply(@NotNull ImmutableSeq<Arg<Term>> args) {
      if (isZero()) {
        assert args.isEmpty();
        return from(0);
      }

      // suc
      assert args.sizeEquals(1);
      var arg = args.get(0).term();
      if (arg instanceof IntegerTerm intTerm) {
        return from(intTerm.repr() + 1);
      }

      return null;
    }

    public @NotNull ConRule update(@NotNull DataCall paramType) {
      return paramType == this.paramType
        ? this
        : new ConRule(ref, paramRecognition, paramType);
    }

    @Override
    public @NotNull Term descent(@NotNull UnaryOperator<Term> f, @NotNull UnaryOperator<Pat> g) {
      return update((DataCall) f.apply(this.paramType));
    }
  }

  record FnRule(
    @Override @NotNull DefVar<FnDef, TeleDecl.FnDecl> ref,
    @Override @NotNull ShapeRecognition paramRecognition,
    @Override @NotNull DataCall paramType,
    @NotNull Kind kind
  ) implements IntegerOpsTerm<FnDef, TeleDecl.FnDecl> {
    public enum Kind implements Serializable {
      Add, SubTrunc
    }

    @Override
    public @Nullable Term apply(@NotNull ImmutableSeq<Arg<Term>> args) {
      return switch (kind) {
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

    public @NotNull FnRule update(@NotNull DataCall paramType) {
      return paramType == this.paramType
        ? this
        : new FnRule(ref, paramRecognition, paramType, kind);
    }

    @Override
    public @NotNull Term descent(@NotNull UnaryOperator<Term> f, @NotNull UnaryOperator<Pat> g) {
      return update((DataCall) f.apply(paramType));
    }
  }
}
