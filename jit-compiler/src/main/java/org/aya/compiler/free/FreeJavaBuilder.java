// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.free;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.immutable.primitive.ImmutableIntSeq;
import org.aya.compiler.free.data.FieldData;
import org.aya.compiler.free.data.LocalVariable;
import org.aya.compiler.free.data.MethodData;
import org.aya.syntax.compile.CompiledAya;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.constant.ClassDesc;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.ObjIntConsumer;

public interface FreeJavaBuilder<Carrier> {
  interface ClassBuilder {
    void buildNestedClass(
      CompiledAya compiledAya,
      @NotNull String name,
      @NotNull Class<?> superclass,
      @NotNull Consumer<ClassBuilder> builder
    );

    @NotNull MethodData buildMethod(
      @NotNull ClassDesc returnType,
      @NotNull String name,
      @NotNull ImmutableSeq<ClassDesc> paramTypes,
      @NotNull BiConsumer<ArgumentProvider, CodeBuilder> builder
    );

    void buildConstructor(
      @NotNull ImmutableSeq<ClassDesc> superConParamTypes,
      @NotNull ImmutableSeq<FreeJava> superConArgs,
      @NotNull ImmutableSeq<ClassDesc> paramTypes,
      @NotNull BiConsumer<ArgumentProvider, CodeBuilder> builder
    );

    @NotNull FieldData buildConstantField(
      @NotNull ClassDesc returnType,
      @NotNull String name
    );
  }

  interface CodeBuilder {
    @NotNull LocalVariable makeVar(@NotNull ClassDesc type, @Nullable FreeJava initializer);

    void ifNotTrue(@NotNull FreeJava notTrue, @NotNull Consumer<CodeBuilder> thenBlock, @Nullable Consumer<CodeBuilder> elseBlock);
    void ifTrue(@NotNull FreeJava theTrue, @NotNull Consumer<CodeBuilder> thenBlock, @Nullable Consumer<CodeBuilder> elseBlock);
    void ifInstanceOf(@NotNull FreeJava lhs, @NotNull ClassDesc rhs, @NotNull BiConsumer<CodeBuilder, LocalVariable> thenBlock, @Nullable Consumer<CodeBuilder> elseBlock);
    void ifNull(@NotNull FreeJava isNull, @NotNull Consumer<CodeBuilder> thenBlock, @Nullable Consumer<CodeBuilder> elseBlock);

    /**
     * Construct a code block that can jump out
     */
    void breakable(@NotNull Consumer<CodeBuilder> innerBlock);
    void breakOut();

    /**
     * Build a switch statement on int
     */
    void switchCase(
      @NotNull FreeJava elim,
      @NotNull ImmutableIntSeq cases,
      @NotNull ObjIntConsumer<CodeBuilder> branch,
      @NotNull Consumer<CodeBuilder> defaultCase
    );

    void returnWith(@NotNull FreeJava expr);

    // region expr

    /**
     * A {@code new} expression, the class should have only one (public) constructor.
     */
    @NotNull FreeJava newObject(@NotNull ClassDesc className, @NotNull ImmutableSeq<FreeJava> args);
    @NotNull FreeJava refVar(@NotNull LocalVariable name);

    /** Invoke a (non-interface) method on {@param owner} */
    @NotNull FreeJava invoke(@NotNull MethodData method, @NotNull FreeJava owner, @NotNull ImmutableSeq<FreeJava> args);

    /** Invoke a static method */
    @NotNull FreeJava invoke(@NotNull MethodData method, @NotNull ImmutableSeq<FreeJava> args);

    @NotNull FreeJava refField(@NotNull FieldData field);
    @NotNull FreeJava refField(@NotNull FieldData field, @NotNull FreeJava owner);

    @NotNull FreeJava mkLambda(
      @NotNull ImmutableSeq<FreeJava> captures,
      @NotNull MethodData method,
      @NotNull Function<ArgumentProvider.Lambda, FreeJava> builder
    );

    // endregion expr
  }

  @NotNull Carrier buildClass(
    @NotNull CompiledAya compiledAya,
    @NotNull ClassDesc className,
    @NotNull Class<?> superclass,
    @NotNull Consumer<ClassBuilder> builder
  );

  /**
   * Find a method with given information
   */
  @NotNull MethodData resolve(
    @NotNull ClassDesc owner,
    @NotNull String name,
    @NotNull ClassDesc returnType,
    @NotNull ImmutableSeq<ClassDesc> paramType
  );
}
