// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.serializers;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import org.aya.compiler.morphism.Constants;
import org.aya.compiler.morphism.ast.AstArgumentProvider;
import org.aya.compiler.morphism.ast.AstVariable;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.lang.constant.ClassDesc;

///
public class InvokeSignatureHelper {
  private final @NotNull ImmutableSeq<ClassDesc> extraParams;

  public InvokeSignatureHelper(@NotNull ImmutableSeq<ClassDesc> extraParams) { this.extraParams = extraParams; }

  public @NotNull ImmutableSeq<ClassDesc> parameters() {
    return extraParams.prepended(Constants.CD_UnaryOperator);
  }

  public static @NotNull ImmutableSeq<ClassDesc> parameters(@NotNull SeqView<ClassDesc> extraParams) {
    return extraParams.prepended(Constants.CD_UnaryOperator).toSeq();
  }

  public static @NotNull AstVariable normalizer(@NotNull AstArgumentProvider ap) {
    return ap.arg(0);
  }

  @Contract(pure = true)
  public static @NotNull AstVariable normalizer(AstArgumentProvider.Lambda ap) {
    return ap.capture(0);
  }

  public static @NotNull AstVariable arg(@NotNull AstArgumentProvider ap, int nth) {
    return ap.arg(1 + nth);
  }

  @Contract(pure = true)
  public static @NotNull AstVariable capture(@NotNull AstArgumentProvider.Lambda ap, int nth) {
    return ap.capture(1 + nth);
  }

  public static @NotNull ImmutableSeq<AstVariable> args(@NotNull AstVariable normalizer, @NotNull SeqView<AstVariable> args) {
    return args.prepended(normalizer).toSeq();
  }
}
