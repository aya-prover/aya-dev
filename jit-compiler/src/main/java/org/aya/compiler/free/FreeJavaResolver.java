// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.free;

import kala.collection.immutable.ImmutableSeq;
import org.aya.compiler.free.data.FieldData;
import org.aya.compiler.free.data.MethodData;
import org.aya.util.error.Panic;
import org.jetbrains.annotations.NotNull;

import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.util.Arrays;

public interface FreeJavaResolver {
  /**
   * Find a method with given information
   */
  @NotNull MethodData resolve(
    @NotNull ClassDesc owner,
    @NotNull String name,
    @NotNull ClassDesc returnType,
    @NotNull ImmutableSeq<ClassDesc> paramType,
    boolean isInterface
  );

  @NotNull FieldData resolve(
    @NotNull ClassDesc owner,
    @NotNull String name,
    @NotNull ClassDesc returnType
  );

  default @NotNull FieldData resolve(
    @NotNull Class<?> owner,
    @NotNull String name
  ) {
    try {
      var field = owner.getField(name);
      return resolve(FreeUtils.fromClass(owner), name, FreeUtils.fromClass(field.getType()));
    } catch (NoSuchFieldException e) {
      throw new Panic(e);
    }
  }

  /**
   * Find the only method with given name
   */
  default @NotNull MethodData resolve(@NotNull Class<?> owner, @NotNull String name, int paramSize) {
    if (name.equals(ConstantDescs.INIT_NAME)) {
      throw new Panic("use ExprBuilder#newObject instead");
    }

    var found = Arrays.stream(owner.getMethods())
      .filter(m -> m.getName().equals(name) && m.getParameterCount() == paramSize)
      .toList();

    assert found.size() == 1;

    var reallyFound = found.getFirst();

    return resolve(
      FreeUtils.fromClass(owner),
      name,
      FreeUtils.fromClass(reallyFound.getReturnType()),
      ImmutableSeq.from(reallyFound.getParameterTypes()).map(FreeUtils::fromClass),
      reallyFound.getDeclaringClass().isInterface()
    );
  }
}
