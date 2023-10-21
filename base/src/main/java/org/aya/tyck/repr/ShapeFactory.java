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
  public static @Nullable Shaped.Applicable<Term, CtorDef, TeleDecl.DataCtor> ofCtor(
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

  public static @Nullable Shaped.Applicable<Term, CtorDef, TeleDecl.DataCtor> ofCtor(
    @NotNull DefVar<CtorDef, TeleDecl.DataCtor> ref,
    @NotNull ShapeRecognition paramRecog,
    @NotNull DataCall paramType
  ) {
    if (paramRecog.shape() == AyaShape.NAT_SHAPE) {
      return new IntegerOpsTerm.ConRule(ref, paramRecog, paramType);
    }

    return null;
  }

  public static @Nullable Shaped.Applicable<Term, FnDef, TeleDecl.FnDecl> ofFn(
    @NotNull DefVar<FnDef, TeleDecl.FnDecl> ref,
    @NotNull ShapeRecognition recog,
    @NotNull AyaShape.Factory factory
  ) {
    var core = ref.core;
    if (core == null) return null;

    if (recog.shape() == AyaShape.PLUS_LEFT_SHAPE || recog.shape() == AyaShape.PLUS_RIGHT_SHAPE) {
      if (!(core.result instanceof DataCall paramType)) return null;
      var dataDef = paramType.ref().core;
      assert dataDef != null : "How?";

      var paramRecog = factory.find(dataDef).getOrNull();
      if (paramRecog == null) throw new InternalException("How?");

      return new IntegerOpsTerm.FnRule(ref, paramRecog, paramType, IntegerOpsTerm.FnRule.Kind.Add);
    }

    return null;
  }
}
