// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.serializers;

import kala.collection.immutable.ImmutableSeq;
import org.aya.compiler.free.Constants;
import org.aya.compiler.free.FreeCodeBuilder;
import org.aya.compiler.free.FreeExprBuilder;
import org.aya.compiler.free.FreeJavaExpr;
import org.aya.syntax.core.term.Term;
import org.aya.util.Panic;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

import static org.aya.compiler.serializers.ExprializeUtil.getJavaRef;

public interface AyaSerializer {
  String PACKAGE_BASE = "AYA";
  String STATIC_FIELD_INSTANCE = "INSTANCE";
  String FIELD_INSTANCE = "ref";
  String FIELD_EMPTYCALL = "ourCall";
  String CLASS_PANIC = getJavaRef(Panic.class);

  static void execPanic(@NotNull FreeCodeBuilder builder) {
    builder.exec(builder.invoke(Constants.PANIC, ImmutableSeq.empty()));
  }

  /**
   * Build a type safe {@link Supplier#get()}, note that this method assume the result is {@link Term}
   */
  static @NotNull FreeJavaExpr getThunk(@NotNull FreeExprBuilder builder, @NotNull FreeJavaExpr thunkExpr) {
    return builder.checkcast(builder.invoke(Constants.THUNK, thunkExpr, ImmutableSeq.empty()), Term.class);
  }
}
