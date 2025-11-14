// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.serializers;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import org.aya.compiler.morphism.Constants;
import org.aya.compiler.morphism.ir.IrValue;
import org.aya.compiler.morphism.ir.IrVariable;
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

  public static @NotNull IrVariable normalizerInFn() {
    return new IrVariable.Arg(0);
  }

  @Contract(pure = true)
  public static @NotNull IrVariable normalizerInLam() {
    return new IrVariable.Capture(0);
  }

  public static @NotNull IrVariable arg(int nth) {
    return new IrVariable.Arg(1 + nth);
  }

  @Contract(pure = true)
  public static @NotNull IrVariable capture(int nth) {
    return new IrVariable.Capture(1 + nth);
  }

  public static @NotNull ImmutableSeq<IrValue> args(@NotNull IrVariable normalizer, @NotNull SeqView<IrValue> args) {
    return args.prepended(normalizer).toSeq();
  }
}
