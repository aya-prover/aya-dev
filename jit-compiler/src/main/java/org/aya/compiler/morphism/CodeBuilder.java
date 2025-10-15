// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.morphism;

import kala.collection.immutable.ImmutableArray;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.immutable.primitive.ImmutableIntSeq;
import org.aya.compiler.FieldRef;
import org.aya.compiler.LocalVariable;
import org.aya.compiler.MethodRef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.constant.ClassDesc;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.ObjIntConsumer;

public interface CodeBuilder {
  @NotNull LocalVariable makeVar(@NotNull ClassDesc type, @Nullable JavaExpr initializer);

  default LocalVariable makeVar(@NotNull Class<?> type, @Nullable JavaExpr initializer) {
    return makeVar(AstUtil.fromClass(type), initializer);
  }

  void invokeSuperCon(@NotNull ImmutableSeq<ClassDesc> superConParams, @NotNull ImmutableSeq<JavaExpr> superConArgs);
  void updateVar(@NotNull LocalVariable var, @NotNull JavaExpr update);
  void updateArray(@NotNull JavaExpr array, int idx, @NotNull JavaExpr update);
  void ifNotTrue(@NotNull LocalVariable notTrue, @NotNull Consumer<CodeBuilder> thenBlock, @Nullable Consumer<CodeBuilder> elseBlock);
  void ifTrue(@NotNull LocalVariable theTrue, @NotNull Consumer<CodeBuilder> thenBlock, @Nullable Consumer<CodeBuilder> elseBlock);
  void ifInstanceOf(@NotNull JavaExpr lhs, @NotNull ClassDesc rhs, @NotNull BiConsumer<CodeBuilder, LocalVariable> thenBlock, @Nullable Consumer<CodeBuilder> elseBlock);
  void ifIntEqual(@NotNull JavaExpr lhs, int rhs, @NotNull Consumer<CodeBuilder> thenBlock, @Nullable Consumer<CodeBuilder> elseBlock);
  void ifRefEqual(@NotNull JavaExpr lhs, @NotNull JavaExpr rhs, @NotNull Consumer<CodeBuilder> thenBlock, @Nullable Consumer<CodeBuilder> elseBlock);
  void ifNull(@NotNull JavaExpr isNull, @NotNull Consumer<CodeBuilder> thenBlock, @Nullable Consumer<CodeBuilder> elseBlock);

  /// Construct a code block that can jump out
  void breakable(@NotNull Consumer<CodeBuilder> innerBlock);
  void breakOut();

  void whileTrue(@NotNull Consumer<CodeBuilder> innerBlock);
  void continueLoop();

  /// Turns an expression to a statement
  void exec(@NotNull JavaExpr expr);

  /// Build a switch statement on int
  ///
  /// @apiNote the {@param branch}es must return or [#breakOut], this method will NOT generate the `break`
  /// instruction therefore the control flow will pass to the next case.
  void switchCase(
    @NotNull LocalVariable elim,
    @NotNull ImmutableIntSeq cases,
    @NotNull ObjIntConsumer<CodeBuilder> branch,
    @NotNull Consumer<CodeBuilder> defaultCase
  );

  void returnWith(@NotNull JavaExpr expr);

  default void unreachable() {
    returnWith(invoke(Constants.PANIC, ImmutableSeq.empty()));
  }

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
