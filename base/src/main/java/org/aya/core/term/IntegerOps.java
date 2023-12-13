// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

import kala.collection.immutable.ImmutableSeq;
import org.aya.concrete.stmt.decl.TeleDecl;
import org.aya.core.def.CtorDef;
import org.aya.core.def.Def;
import org.aya.core.def.FnDef;
import org.aya.core.repr.CodeShape;
import org.aya.core.repr.ShapeRecognition;
import org.aya.generic.Shaped;
import org.aya.ref.DefVar;
import org.aya.util.Arg;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;

/**
 * IntegerOps acts like a DefVar with special reduce rule. So it is not a {@link Term}.
 *
 * @see RuleReducer
 */
public sealed interface IntegerOps<Core extends Def, Concrete extends TeleDecl<?>>
  extends Shaped.Applicable<Term, Core, Concrete> {
  @Override default @NotNull Term type() {
    var core = ref().core;
    assert core != null;
    return PiTerm.make(core.telescope(), core.result());
  }

  record ConRule(
    @Override @NotNull DefVar<CtorDef, TeleDecl.DataCtor> ref,
    @Override @NotNull ShapeRecognition paramRecognition,
    @Override @NotNull DataCall paramType
  ) implements IntegerOps<CtorDef, TeleDecl.DataCtor> {
    public boolean isZero() {
      return paramRecognition.captures().get(CodeShape.GlobalId.ZERO) == ref;
    }

    @Override
    public @Nullable Term apply(@NotNull ImmutableSeq<Arg<Term>> args) {
      if (isZero()) {
        assert args.isEmpty();
        return new IntegerTerm(0, paramRecognition, paramType);
      }

      // suc
      assert args.sizeEquals(1);
      var arg = args.get(0).term();
      if (arg instanceof IntegerTerm intTerm) {
        return intTerm.map(x -> x + 1);
      }

      return null;
    }
  }

  record FnRule(
    @Override @NotNull DefVar<FnDef, TeleDecl.FnDecl> ref,
    @NotNull Kind kind
  ) implements IntegerOps<FnDef, TeleDecl.FnDecl> {
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
            yield ita.map(x -> x + itb.repr());
          }

          yield null;
        }
        case SubTrunc -> {
          assert args.sizeEquals(2);
          var a = args.get(0).term();
          var b = args.get(1).term();
          if (a instanceof IntegerTerm ita && b instanceof IntegerTerm itb) {
            yield ita.map(x -> Math.max(x - itb.repr(), 0));
          }

          yield null;
        }
      };
    }
  }
}
