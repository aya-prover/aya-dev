// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.free;

import kala.collection.immutable.ImmutableSeq;
import org.aya.compiler.free.data.FieldData;
import org.aya.compiler.free.data.LocalVariable;
import org.aya.compiler.free.data.MethodData;
import org.jetbrains.annotations.NotNull;

import java.lang.constant.ClassDesc;
import java.util.function.Function;

public interface FreeExprBuilder {
  @NotNull FreeJavaResolver resolver();

  /**
   * A {@code new} expression, the class should have only one (public) constructor.
   */
  @NotNull FreeJava mkNew(@NotNull ClassDesc className, @NotNull ImmutableSeq<FreeJava> args);

  default @NotNull FreeJava mkNew(@NotNull Class<?> className, @NotNull ImmutableSeq<FreeJava> args) {
    return mkNew(FreeUtils.fromClass(className), args);
  }

  @NotNull FreeJava refVar(@NotNull LocalVariable name);

  /** Invoke a (non-interface) method on {@param owner} */
  @NotNull FreeJava invoke(@NotNull MethodData method, @NotNull FreeJava owner, @NotNull ImmutableSeq<FreeJava> args);

  /** Invoke a static method */
  @NotNull FreeJava invoke(@NotNull MethodData method, @NotNull ImmutableSeq<FreeJava> args);

  @NotNull FreeJava refField(@NotNull FieldData field);
  @NotNull FreeJava refField(@NotNull FieldData field, @NotNull FreeJava owner);

  @NotNull FreeJava refEnum(@NotNull ClassDesc enumClass, @NotNull String enumName);

  default @NotNull FreeJava refEnum(@NotNull Enum<?> value) {
    var cd = FreeUtils.fromClass(value.getClass());
    var name = value.name();
    return refEnum(cd, name);
  }

  @NotNull FreeJava mkLambda(
    @NotNull ImmutableSeq<FreeJava> captures,
    @NotNull MethodData method,
    @NotNull Function<ArgumentProvider.Lambda, FreeJava> builder
  );

  @NotNull FreeJava iconst(int i);

  @NotNull FreeJava iconst(boolean b);

  @NotNull FreeJava aconst(@NotNull String value);

  @NotNull FreeJava mkArray(
    @NotNull ClassDesc type, int length,
    @NotNull ImmutableSeq<FreeJava> initializer
  );
}
