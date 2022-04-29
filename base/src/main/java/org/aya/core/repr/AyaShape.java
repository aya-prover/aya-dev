// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.repr;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableLinkedHashMap;
import kala.collection.mutable.MutableList;
import kala.collection.mutable.MutableMap;
import org.aya.core.def.CtorDef;
import org.aya.core.def.Def;
import org.aya.core.term.CallTerm;
import org.aya.core.term.LitTerm;
import org.aya.core.term.Term;
import org.aya.generic.Arg;
import org.aya.generic.util.InternalException;
import org.jetbrains.annotations.NotNull;

import java.util.function.BiFunction;
import java.util.function.Function;

import static org.aya.core.repr.CodeShape.CtorShape;
import static org.aya.core.repr.CodeShape.DataShape;

/**
 * @author kiva
 */
public sealed interface AyaShape {
  @NotNull CodeShape codeShape();
  @NotNull Term transformTerm(@NotNull Term term, @NotNull Term type);

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
      var integer = ((LitTerm.ShapedInt) term).integer();
      var dataCall = ((CallTerm.Data) type);
      var dataRef = dataCall.ref();
      return transform(integer, dataCall,
        zero -> new CallTerm.Con(dataRef, zero.ref, ImmutableSeq.empty(), 0, ImmutableSeq.empty()),
        (suc, nat) -> new CallTerm.Con(dataRef, suc.ref, ImmutableSeq.empty(), 0, ImmutableSeq.of(new Arg<>(nat, true))));
    }

    private <T> T transform(int integer, @NotNull CallTerm.Data type,
                            @NotNull Function<CtorDef, T> makeZero,
                            @NotNull BiFunction<CtorDef, T, T> makeSuc) {
      return with(type, (zero, suc) -> {
        var zeroT = makeZero.apply(zero);
        for (int i = 0; i < integer; i++) {
          zeroT = makeSuc.apply(suc, zeroT);
        }
        return zeroT;
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
