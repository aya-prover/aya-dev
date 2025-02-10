// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.free;

import kala.collection.immutable.ImmutableSeq;
import org.aya.compiler.free.data.FieldRef;
import org.aya.compiler.free.data.MethodRef;
import org.aya.util.Panic;
import org.jetbrains.annotations.NotNull;

import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.util.Arrays;

public final class FreeJavaResolver {
  /**
   * Find a method with given information
   */
  public static @NotNull MethodRef resolve(
    @NotNull ClassDesc owner,
    @NotNull String name,
    @NotNull ClassDesc returnType,
    @NotNull ImmutableSeq<ClassDesc> paramType,
    boolean isInterface
  ) {
    return new MethodRef(owner, name, returnType, paramType, isInterface);
  }

  public static @NotNull FieldRef resolve(
    @NotNull ClassDesc owner,
    @NotNull String name,
    @NotNull ClassDesc returnType
  ) {
    return new FieldRef(owner, returnType, name);
  }

  public static @NotNull FieldRef resolve(@NotNull Class<?> owner, @NotNull String name) {
    try {
      var field = owner.getField(name);
      return resolve(FreeUtil.fromClass(owner), name, FreeUtil.fromClass(field.getType()));
    } catch (NoSuchFieldException e) {
      throw new Panic(e);
    }
  }

  /**
   * Find the only method with given name
   */
  public static @NotNull MethodRef resolve(@NotNull Class<?> owner, @NotNull String name, int paramSize) {
    if (name.equals(ConstantDescs.INIT_NAME)) {
      throw new Panic("use ExprBuilder#newObject instead");
    }

    var found = Arrays.stream(owner.getMethods())
      .filter(m -> m.getName().equals(name) && m.getParameterCount() == paramSize)
      .toList();

    assert found.size() == 1;

    var reallyFound = found.getFirst();

    return resolve(
      FreeUtil.fromClass(owner),
      name,
      FreeUtil.fromClass(reallyFound.getReturnType()),
      ImmutableSeq.from(reallyFound.getParameterTypes()).map(FreeUtil::fromClass),
      reallyFound.getDeclaringClass().isInterface()
    );
  }
}
