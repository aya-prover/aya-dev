// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.morphism;

import kala.collection.immutable.ImmutableArray;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.immutable.primitive.ImmutableIntSeq;
import org.aya.compiler.FieldRef;
import org.aya.compiler.MethodRef;
import org.aya.compiler.morphism.ast.AstExpr;
import org.aya.compiler.morphism.ast.AstVariable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.constant.ClassDesc;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.ObjIntConsumer;

public interface CodeBuilder {
  @NotNull AstVariable makeVar(@NotNull ClassDesc type, @Nullable AstExpr initializer);

  void invokeSuperCon(@NotNull ImmutableSeq<ClassDesc> superConParams, @NotNull ImmutableSeq<AstExpr> superConArgs);
  void updateVar(@NotNull AstVariable var, @NotNull AstExpr update);
  void updateArray(@NotNull AstExpr array, int idx, @NotNull AstExpr update);
  void ifNotTrue(@NotNull AstVariable notTrue, @NotNull Consumer<CodeBuilder> thenBlock, @Nullable Consumer<CodeBuilder> elseBlock);
  void ifTrue(@NotNull AstVariable theTrue, @NotNull Consumer<CodeBuilder> thenBlock, @Nullable Consumer<CodeBuilder> elseBlock);
  void ifInstanceOf(@NotNull AstExpr lhs, @NotNull ClassDesc rhs, @NotNull BiConsumer<CodeBuilder, AstVariable> thenBlock, @Nullable Consumer<CodeBuilder> elseBlock);
  void ifIntEqual(@NotNull AstExpr lhs, int rhs, @NotNull Consumer<CodeBuilder> thenBlock, @Nullable Consumer<CodeBuilder> elseBlock);
  void ifRefEqual(@NotNull AstExpr lhs, @NotNull AstExpr rhs, @NotNull Consumer<CodeBuilder> thenBlock, @Nullable Consumer<CodeBuilder> elseBlock);
  void ifNull(@NotNull AstExpr isNull, @NotNull Consumer<CodeBuilder> thenBlock, @Nullable Consumer<CodeBuilder> elseBlock);

  /// Construct a code block that can jump out
  void breakable(@NotNull Consumer<CodeBuilder> innerBlock);
  void breakOut();

  void whileTrue(@NotNull Consumer<CodeBuilder> innerBlock);
  void continueLoop();

  /// Turns an expression to a statement
  void exec(@NotNull AstExpr expr);

  /// Build a switch statement on int
  ///
  /// @apiNote the {@param branch}es must return or [#breakOut], this method will NOT generate the `break`
  /// instruction therefore the control flow will pass to the next case.
  void switchCase(
    @NotNull AstVariable elim,
    @NotNull ImmutableIntSeq cases,
    @NotNull ObjIntConsumer<CodeBuilder> branch,
    @NotNull Consumer<CodeBuilder> defaultCase
  );

  void returnWith(@NotNull AstExpr expr);

  default void unreachable() {
    returnWith(invoke(Constants.PANIC, ImmutableSeq.empty()));
  }

  @NotNull AstExpr mkNew(@NotNull MethodRef conRef, @NotNull ImmutableSeq<AstExpr> args);

  /// A `new` expression, the class should have only one (public) constructor with parameter count `args.size()`.
  default @NotNull AstExpr mkNew(@NotNull Class<?> className, @NotNull ImmutableSeq<AstExpr> args) {
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

  /// Invoke a (non-interface) method on {@param owner}.
  @NotNull AstExpr invoke(@NotNull MethodRef method, @NotNull AstExpr owner, @NotNull ImmutableSeq<AstExpr> args);

  /// Invoke a static method
  @NotNull AstExpr invoke(@NotNull MethodRef method, @NotNull ImmutableSeq<AstExpr> args);

  @NotNull AstExpr refField(@NotNull FieldRef field);
  @NotNull AstExpr refField(@NotNull FieldRef field, @NotNull AstExpr owner);
  @NotNull AstExpr refEnum(@NotNull ClassDesc enumClass, @NotNull String enumName);

  default @NotNull AstExpr refEnum(@NotNull Enum<?> value) {
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
  @NotNull AstExpr mkLambda(
    @NotNull ImmutableSeq<AstExpr> captures,
    @NotNull MethodRef method,
    @NotNull BiConsumer<ArgumentProvider.Lambda, CodeBuilder> builder
  );

  @NotNull AstExpr iconst(int i);
  @NotNull AstExpr iconst(boolean b);
  @NotNull AstExpr aconst(@NotNull String value);
  @NotNull AstExpr aconstNull(@NotNull ClassDesc type);
  @NotNull AstExpr thisRef();

  /**
   * Construct an array with given type and have length {@param length}
   *
   * @param initializer the initializer, the size is either {@code 0} or {@param length}, 0-length means don't initialize
   */
  @NotNull AstExpr mkArray(
    @NotNull ClassDesc type, int length,
    @Nullable ImmutableSeq<AstExpr> initializer
  );

  @NotNull AstExpr getArray(@NotNull AstExpr array, int index);
  @NotNull AstExpr checkcast(@NotNull AstExpr obj, @NotNull ClassDesc as);

  default AstExpr checkcast(@NotNull AstExpr obj, @NotNull Class<?> as) {
    return checkcast(obj, AstUtil.fromClass(as));
  }
}
