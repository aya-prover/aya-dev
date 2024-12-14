// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.free;

import kala.collection.immutable.ImmutableSeq;
import org.aya.compiler.free.data.FieldRef;
import org.aya.compiler.free.data.LocalVariable;
import org.aya.compiler.free.data.MethodRef;
import org.jetbrains.annotations.NotNull;

import java.lang.constant.ClassDesc;
import java.util.function.BiConsumer;

/**
 * the result only depends on the {@link FreeCodeBuilder} that this builder derived from
 */
public interface FreeExprBuilder {
  @NotNull FreeJavaResolver resolver();

  /**
   * A {@code new} expression, the class should have only one (public) constructor with parameter count {@code args.size()}.
   */
  @NotNull FreeJavaExpr mkNew(@NotNull ClassDesc className, @NotNull ImmutableSeq<FreeJavaExpr> args);

  default @NotNull FreeJavaExpr mkNew(@NotNull Class<?> className, @NotNull ImmutableSeq<FreeJavaExpr> args) {
    return mkNew(FreeUtil.fromClass(className), args);
  }

  @NotNull FreeJavaExpr refVar(@NotNull LocalVariable name);

  /**
   * Invoke a (non-interface) method on {@param owner}.
   * Remember to {@link FreeCodeBuilder#exec(FreeJavaExpr)} if you do not need the result!
   */
  @NotNull FreeJavaExpr invoke(@NotNull MethodRef method, @NotNull FreeJavaExpr owner, @NotNull ImmutableSeq<FreeJavaExpr> args);

  /** Invoke a static method */
  @NotNull FreeJavaExpr invoke(@NotNull MethodRef method, @NotNull ImmutableSeq<FreeJavaExpr> args);

  @NotNull FreeJavaExpr refField(@NotNull FieldRef field);

  @NotNull FreeJavaExpr refField(@NotNull FieldRef field, @NotNull FreeJavaExpr owner);

  @NotNull FreeJavaExpr refEnum(@NotNull ClassDesc enumClass, @NotNull String enumName);

  default @NotNull FreeJavaExpr refEnum(@NotNull Enum<?> value) {
    var cd = FreeUtil.fromClass(value.getClass());
    var name = value.name();
    return refEnum(cd, name);
  }

  @NotNull FreeJavaExpr mkLambda(
    @NotNull ImmutableSeq<FreeJavaExpr> captures,
    @NotNull MethodRef method,
    @NotNull BiConsumer<ArgumentProvider.Lambda, FreeCodeBuilder> builder
  );

  @NotNull FreeJavaExpr iconst(int i);

  @NotNull FreeJavaExpr iconst(boolean b);

  @NotNull FreeJavaExpr aconst(@NotNull String value);

  @NotNull FreeJavaExpr aconstNull(@NotNull ClassDesc type);

  @NotNull FreeJavaExpr thisRef();

  /**
   * Construct an array with given type and have length {@param length}
   *
   * @param initializer the initializer, the size is either {@code 0} or {@param length}, 0-length means don't initialize
   */
  @NotNull FreeJavaExpr mkArray(
    @NotNull ClassDesc type, int length,
    @NotNull ImmutableSeq<FreeJavaExpr> initializer
  );

  @NotNull FreeJavaExpr getArray(@NotNull FreeJavaExpr array, int index);

  @NotNull FreeJavaExpr checkcast(@NotNull FreeJavaExpr obj, @NotNull ClassDesc as);

  default FreeJavaExpr checkcast(@NotNull FreeJavaExpr obj, @NotNull Class<?> as) {
    return checkcast(obj, FreeUtil.fromClass(as));
  }
}
