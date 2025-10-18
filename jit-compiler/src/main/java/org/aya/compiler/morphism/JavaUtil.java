// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.morphism;

import kala.collection.immutable.ImmutableSeq;
import org.aya.compiler.MethodRef;
import org.jetbrains.annotations.NotNull;

import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;

public interface JavaUtil {
  static @NotNull ClassDesc fromClass(@NotNull Class<?> clazz) {
    return ClassDesc.ofDescriptor(clazz.descriptorString());
  }

  static @NotNull MethodRef makeConstructorRef(@NotNull ClassDesc owner, @NotNull ImmutableSeq<ClassDesc> parameterTypes) {
    return new MethodRef(owner, ConstantDescs.INIT_NAME, ConstantDescs.CD_void, parameterTypes, false);
  }
}
