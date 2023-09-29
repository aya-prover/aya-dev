// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

import kala.collection.immutable.ImmutableSeq;
import org.aya.concrete.stmt.decl.TeleDecl;
import org.aya.core.def.FnDef;
import org.aya.core.repr.ShapeRecognition;
import org.aya.generic.Shaped;
import org.aya.ref.DefVar;
import org.aya.util.Arg;
import org.jetbrains.annotations.NotNull;

public record IntegerOpsTerm(
  @NotNull DefVar<? extends FnDef, ? extends TeleDecl.FnDecl> defFn,
  @NotNull Kind kind,
  @NotNull ShapeRecognition paramRecog,
  @NotNull DataCall paramType
) implements Shaped.Fn<Term> {
  @Override public @NotNull Term type() {
    assert defFn.core != null;
    return PiTerm.make(defFn.core.telescope, defFn.core.result);
  }

  public enum Kind {
    Zero, Succ,
    Add, SubTrunc
  }

  @Override public @NotNull Term apply(@NotNull ImmutableSeq<Arg<Term>> args) {
    return switch (kind) {
      case Zero -> {
        assert args.isEmpty();
        yield new IntegerTerm(0, paramRecog, paramType);
      }
      case Succ -> {
        assert args.sizeEquals(1);
        var arg = args.get(0).term();
        if (arg instanceof IntegerTerm it) {
          yield new IntegerTerm(it.repr() + 1, paramRecog, paramType);
        } else {
          throw new UnsupportedOperationException("TODO: implement succ");
        }
      }
      case Add -> {
        assert args.sizeEquals(2);
        var a = args.get(0).term();
        var b = args.get(1).term();
        if (a instanceof IntegerTerm ita && b instanceof IntegerTerm itb) {
          yield new IntegerTerm(ita.repr() + itb.repr(), paramRecog, paramType);
        } else {
          throw new UnsupportedOperationException("TODO: implement add");
        }
      }
      case SubTrunc -> {
        assert args.sizeEquals(2);
        var a = args.get(0).term();
        var b = args.get(1).term();
        if (a instanceof IntegerTerm ita && b instanceof IntegerTerm itb) {
          yield new IntegerTerm(Math.max(ita.repr() - itb.repr(), 0), paramRecog, paramType);
        } else {
          throw new UnsupportedOperationException("TODO: implement subtrunc");
        }
      }
    };
  }
}
