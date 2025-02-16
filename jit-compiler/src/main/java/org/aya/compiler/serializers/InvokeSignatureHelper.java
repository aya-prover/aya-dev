// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.serializers;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import org.aya.compiler.LocalVariable;
import org.aya.compiler.morphism.ArgumentProvider;
import org.aya.compiler.morphism.Constants;
import org.aya.compiler.morphism.JavaExpr;
import org.jetbrains.annotations.NotNull;

import java.lang.constant.ClassDesc;

public class InvokeSignatureHelper {
  private final @NotNull ImmutableSeq<ClassDesc> extraParams;

  public InvokeSignatureHelper(@NotNull ImmutableSeq<ClassDesc> extraParams) { this.extraParams = extraParams; }

  public @NotNull ImmutableSeq<ClassDesc> parameters() {
    return extraParams.prepended(Constants.CD_UnaryOperator);
  }

  public static @NotNull LocalVariable normalizer(@NotNull ArgumentProvider ap) {
    return ap.arg(0);
  }

  public static @NotNull LocalVariable arg(@NotNull ArgumentProvider ap, int nth) {
    return ap.arg(nth + 1);
  }

  public static @NotNull ImmutableSeq<JavaExpr> args(@NotNull JavaExpr normalizer, @NotNull SeqView<JavaExpr> args) {
    return args.prepended(normalizer).toSeq();
  }
}
