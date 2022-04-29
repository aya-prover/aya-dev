// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.repr;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableLinkedHashMap;
import kala.collection.mutable.MutableList;
import kala.collection.mutable.MutableMap;
import org.aya.core.def.CtorDef;
import org.aya.core.def.Def;
import org.aya.core.pat.Pat;
import org.aya.core.term.CallTerm;
import org.aya.core.term.LitTerm;
import org.aya.core.term.Term;
import org.aya.generic.Arg;
import org.aya.generic.util.InternalException;
import org.jetbrains.annotations.NotNull;

import java.util.function.BiFunction;

import static org.aya.core.repr.CodeShape.CtorShape;
import static org.aya.core.repr.CodeShape.DataShape;

/**
 * @author kiva
 */
public sealed interface AyaShape {
  @NotNull CodeShape codeShape();
  @NotNull Term transformTerm(@NotNull Term term, @NotNull Term type);
  @NotNull Pat transformPat(@NotNull Pat pat, @NotNull Term type);

  @NotNull CodeShape DATA_NAT = new DataShape(ImmutableSeq.empty(), ImmutableSeq.of(
    new CtorShape(ImmutableSeq.empty()),
    new CtorShape(ImmutableSeq.of(CodeShape.ParamShape.ex(new CodeShape.TermShape.Call(0))))
  ));

  @NotNull AyaShape NAT_SHAPE = new AyaIntLitShape();
  @NotNull ImmutableSeq<AyaShape> LITERAL_SHAPES = ImmutableSeq.of(NAT_SHAPE);

  record AyaIntLitShape() implements AyaShape {
    @Override public @NotNull CodeShape codeShape() {
      return DATA_NAT;
    }

    @Override public @NotNull Term transformTerm(@NotNull Term term, @NotNull Term type) {
      assert type instanceof CallTerm.Data;
      assert term instanceof LitTerm.ShapedInt;
      var litTerm = ((LitTerm.ShapedInt) term);
      var integer = ((LitTerm.ShapedInt) term).integer();
      var dataCall = ((CallTerm.Data) type);
      return with(dataCall, (zero, suc) -> {
        if (integer == 0) return new CallTerm.Con(dataCall.ref(), zero.ref, ImmutableSeq.empty(), 0, ImmutableSeq.empty());
        return new CallTerm.Con(dataCall.ref(), suc.ref, ImmutableSeq.empty(), 0, ImmutableSeq.of(new Arg<>(
          new LitTerm.ShapedInt(integer - 1, litTerm.shape(), litTerm.type()), true)));
      });
    }

    @Override public @NotNull Pat transformPat(@NotNull Pat pat, @NotNull Term type) {
      assert type instanceof CallTerm.Data;
      assert pat instanceof Pat.ShapedInt;
      var litPat = ((Pat.ShapedInt) pat);
      var integer = ((Pat.ShapedInt) pat).integer();
      var dataCall = (CallTerm.Data) type;
      return with(dataCall, (zero, suc) -> {
        if (integer == 0) return new Pat.Ctor(pat.explicit(), zero.ref, ImmutableSeq.empty(), dataCall);
        return new Pat.Ctor(pat.explicit(), suc.ref, ImmutableSeq.of(
          new Pat.ShapedInt(integer - 1, litPat.shape(), dataCall, pat.explicit())),
          dataCall);
      });
    }

    private <T> T with(@NotNull CallTerm.Data type, @NotNull BiFunction<CtorDef, CtorDef, T> block) {
      var dataDef = type.ref().core;
      var zeroOpt = dataDef.body.find(it -> it.selfTele.sizeEquals(0));
      var sucOpt = dataDef.body.find(it -> it.selfTele.sizeEquals(1));
      if (zeroOpt.isEmpty() || sucOpt.isEmpty()) throw new InternalException("shape recognition bug");
      var zero = zeroOpt.get();
      var suc = sucOpt.get();
      return block.apply(zero, suc);
    }
  }

  record Factory(@NotNull MutableMap<Def, MutableList<AyaShape>> discovered) {
    public Factory() {
      this(MutableLinkedHashMap.of());
    }
  }
}
