// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.free.data;

import kala.collection.immutable.ImmutableSeq;
import org.jetbrains.annotations.NotNull;

import java.lang.constant.ClassDesc;

public interface MethodData {
  record Default(
    @Override @NotNull ClassDesc owner,
    @Override @NotNull String name,
    @Override @NotNull ClassDesc returnType,
    @Override @NotNull ImmutableSeq<ClassDesc> paramTypes,
    @Override boolean isInterface
  ) implements MethodData { }

  @NotNull ClassDesc owner();
  @NotNull String name();
  @NotNull ClassDesc returnType();
  @NotNull ImmutableSeq<ClassDesc> paramTypes();

  boolean isInterface();
}
