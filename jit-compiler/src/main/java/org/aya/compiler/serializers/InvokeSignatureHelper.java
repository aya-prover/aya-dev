// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.serializers;

import kala.collection.immutable.ImmutableSeq;
import org.aya.compiler.free.ArgumentProvider;
import org.aya.compiler.free.Constants;
import org.aya.compiler.free.data.LocalVariable;
import org.jetbrains.annotations.NotNull;

import java.lang.constant.ClassDesc;

public class InvokeSignatureHelper {
  private final @NotNull ImmutableSeq<ClassDesc> extraParams;

  public InvokeSignatureHelper(@NotNull ImmutableSeq<ClassDesc> extraParams) { this.extraParams = extraParams; }

  public @NotNull ImmutableSeq<ClassDesc> parameters() {
    return extraParams.prepended(Constants.CD_UnaryOperator);
  }

  public @NotNull LocalVariable normalizer(@NotNull ArgumentProvider ap) {
    return ap.arg(0);
  }

  public @NotNull LocalVariable arg(@NotNull ArgumentProvider ap, int nth) {
    return ap.arg(nth + 1);
  }
}
