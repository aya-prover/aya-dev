// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.repr;

import kala.collection.immutable.ImmutableSeq;
import org.aya.concrete.stmt.decl.TeleDecl;
import org.aya.core.def.CtorDef;
import org.aya.core.def.DataDef;
import org.aya.core.def.FnDef;
import org.aya.core.repr.AyaShape;
import org.aya.core.repr.CodeShape;
import org.aya.core.repr.ShapeRecognition;
import org.aya.core.term.DataCall;
import org.aya.core.term.IntegerOpsTerm;
import org.aya.core.term.Term;
import org.aya.generic.Shaped;
import org.aya.ref.DefVar;
import org.aya.util.error.InternalException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ShapeFactory {
  public static @Nullable Shaped.Fn<Term> ofCtor(
    @NotNull DefVar<CtorDef, TeleDecl.DataCtor> ref,
    @NotNull AyaShape.Factory factory,
    @NotNull DataCall paramType
  ) {
    var dataDef = paramType.ref().core;
    assert dataDef != null : "How?";

    var paramRecog = factory.find(dataDef).getOrNull();
    if (paramRecog == null) return null;

    return ofCtor(ref, paramRecog, paramType);
  }

  public static @Nullable Shaped.Fn<Term> ofCtor(
    @NotNull DefVar<CtorDef, TeleDecl.DataCtor> ref,
    @NotNull ShapeRecognition paramRecog,
    @NotNull DataCall paramType
  ) {
    if (paramRecog.shape() == AyaShape.NAT_SHAPE) {
      var kind = ref == paramRecog.captures().get(CodeShape.MomentId.ZERO)
        ? IntegerOpsTerm.Kind.Zero
        : ref == paramRecog.captures().get(CodeShape.MomentId.SUC)
          ? IntegerOpsTerm.Kind.Succ
          : null;

      if (kind == null) throw new InternalException("I need DT");

      return new IntegerOpsTerm(ref, kind, paramRecog, paramType);
    }

    return null;
  }

  public static @Nullable Shaped.Fn<Term> ofFn(
    @NotNull DefVar<FnDef, TeleDecl.FnDecl> ref,
    @NotNull ShapeRecognition recog,
    @NotNull AyaShape.Factory factory
  ) {
    if (recog.shape() == AyaShape.PLUS_LEFT_SHAPE || recog.shape() == AyaShape.PLUS_RIGHT_SHAPE) {
      var dataRef = (DefVar<DataDef, TeleDecl.DataDecl>) recog.captures().get(CodeShape.MomentId.NAT);
      var dataDef = dataRef.core;
      assert dataDef != null : "How?";

      var paramRecog = factory.find(dataDef).getOrNull();
      if (paramRecog == null) throw new InternalException("How?");

      // TODO[h]: Can I use ref.core.result directly?
      var paramType = new DataCall(dataRef, 0, ImmutableSeq.empty());

      return new IntegerOpsTerm(ref, IntegerOpsTerm.Kind.Add, paramRecog, paramType);
    }

    return null;
  }
}
