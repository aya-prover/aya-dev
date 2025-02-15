// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.morphism;

import kala.collection.immutable.ImmutableArray;
import kala.collection.immutable.ImmutableSeq;
import org.aya.compiler.FieldRef;
import org.aya.compiler.LocalVariable;
import org.aya.compiler.MethodRef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.constant.ClassDesc;
import java.util.function.BiConsumer;

/// the result only depends on the [CodeBuilder] that this builder derived from
public interface ExprBuilder {
  /// A `new` expression on specified constructor.
  @NotNull JavaExpr mkNew(@NotNull MethodRef conRef, @NotNull ImmutableSeq<JavaExpr> args);

  /// A `new` expression, the class should have only one (public) constructor with parameter count `args.size()`.
  default @NotNull JavaExpr mkNew(@NotNull Class<?> className, @NotNull ImmutableSeq<JavaExpr> args) {
    var candidates = ImmutableArray.wrap(className.getConstructors())
      .filter(c -> c.getParameterCount() == args.size());

    assert candidates.size() == 1 : "Ambiguous constructors: count " + candidates.size();

    var first = candidates.getFirst();
    var desc = AstUtil.fromClass(className);
    var conRef = ClassBuilder.makeConstructorRef(desc,
      ImmutableArray.wrap(first.getParameterTypes())
        .map(AstUtil::fromClass));
    return mkNew(conRef, args);
  }

  default @NotNull JavaExpr refVar(@NotNull LocalVariable name) { return name.ref(); }

  /// Invoke a (non-interface) method on {@param owner}.
  /// Remember to [#exec(JavaExpr)] if you do not need the result!
  @NotNull JavaExpr invoke(@NotNull MethodRef method, @NotNull JavaExpr owner, @NotNull ImmutableSeq<JavaExpr> args);

  /// Invoke a static method
  @NotNull JavaExpr invoke(@NotNull MethodRef method, @NotNull ImmutableSeq<JavaExpr> args);

  @NotNull JavaExpr refField(@NotNull FieldRef field);
  @NotNull JavaExpr refField(@NotNull FieldRef field, @NotNull JavaExpr owner);
  @NotNull JavaExpr refEnum(@NotNull ClassDesc enumClass, @NotNull String enumName);

  default @NotNull JavaExpr refEnum(@NotNull Enum<?> value) {
    var cd = AstUtil.fromClass(value.getClass());
    var name = value.name();
    return refEnum(cd, name);
  }

  /**
   * Make a lambda expression
   *
   * @param builder the builder for building the lambda body, you should use local variable comes from this and the
   *                {@link ArgumentProvider.Lambda} ONLY, other variables introduced outside of the lambda is unavailable.
   */
  @NotNull JavaExpr mkLambda(
    @NotNull ImmutableSeq<JavaExpr> captures,
    @NotNull MethodRef method,
    @NotNull BiConsumer<ArgumentProvider.Lambda, CodeBuilder> builder
  );

  @NotNull JavaExpr iconst(int i);
  @NotNull JavaExpr iconst(boolean b);
  @NotNull JavaExpr aconst(@NotNull String value);
  @NotNull JavaExpr aconstNull(@NotNull ClassDesc type);
  @NotNull JavaExpr thisRef();

  /**
   * Construct an array with given type and have length {@param length}
   *
   * @param initializer the initializer, the size is either {@code 0} or {@param length}, 0-length means don't initialize
   */
  @NotNull JavaExpr mkArray(
    @NotNull ClassDesc type, int length,
    @Nullable ImmutableSeq<JavaExpr> initializer
  );

  @NotNull JavaExpr getArray(@NotNull JavaExpr array, int index);
  @NotNull JavaExpr checkcast(@NotNull JavaExpr obj, @NotNull ClassDesc as);

  default JavaExpr checkcast(@NotNull JavaExpr obj, @NotNull Class<?> as) {
    return checkcast(obj, AstUtil.fromClass(as));
  }
}
