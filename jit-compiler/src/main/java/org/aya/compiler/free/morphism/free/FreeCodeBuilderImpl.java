// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.free.morphism.free;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.immutable.primitive.ImmutableIntSeq;
import kala.collection.mutable.FreezableMutableList;
import kala.value.MutableValue;
import org.aya.compiler.free.ArgumentProvider;
import org.aya.compiler.free.FreeCodeBuilder;
import org.aya.compiler.free.FreeJavaExpr;
import org.aya.compiler.free.data.FieldRef;
import org.aya.compiler.free.data.LocalVariable;
import org.aya.compiler.free.data.MethodRef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.constant.ClassDesc;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.ObjIntConsumer;

public record FreeCodeBuilderImpl(
  @NotNull FreezableMutableList<FreeStmt> stmts,
  @NotNull VariablePool pool,
  boolean isConstructor,
  boolean isBreakable
) implements FreeCodeBuilder {
  public @NotNull ImmutableSeq<FreeStmt> subscoped(boolean isBreakable, @NotNull Consumer<FreeCodeBuilderImpl> block) {
    var inner = new FreeCodeBuilderImpl(FreezableMutableList.create(), pool, isConstructor, isBreakable);
    block.accept(inner);
    return inner.build();
  }

  public @NotNull ImmutableSeq<FreeStmt> build() {
    return stmts.freeze();
  }

  public static @NotNull FreeExpr assertFreeExpr(@NotNull FreeJavaExpr expr) {
    return (FreeExpr) expr;
  }

  public static @NotNull ImmutableSeq<FreeExpr> assertFreeExpr(@NotNull ImmutableSeq<FreeJavaExpr> exprs) {
    return exprs.map(x -> (FreeExpr) x);
  }

  public static @NotNull FreeVariable assertFreeVariable(@NotNull LocalVariable var) {
    return (FreeVariable) var;
  }

  public @NotNull FreeVariable.Local acquireVariable() {
    return new FreeVariable.Local(pool.acquire());
  }

  @Override
  public @NotNull FreeVariable makeVar(@NotNull ClassDesc type, @Nullable FreeJavaExpr initializer) {
    var theVar = acquireVariable();
    stmts.append(new FreeStmt.DeclareVariable(type, theVar));
    if (initializer != null) updateVar(theVar, initializer);
    return theVar;
  }

  @Override
  public void invokeSuperCon(@NotNull ImmutableSeq<ClassDesc> superConParams, @NotNull ImmutableSeq<FreeJavaExpr> superConArgs) {
    assert isConstructor;
    stmts.append(new FreeStmt.Super(superConParams, assertFreeExpr(superConArgs)));
  }

  @Override
  public void updateVar(@NotNull LocalVariable var, @NotNull FreeJavaExpr update) {
    stmts.append(new FreeStmt.SetVariable(assertFreeVariable(var), assertFreeExpr(update)));
  }

  @Override
  public void updateArray(@NotNull FreeJavaExpr array, int idx, @NotNull FreeJavaExpr update) {
    stmts.append(new FreeStmt.SetArray(assertFreeExpr(array), idx, assertFreeExpr(update)));
  }

  private void buildIf(@NotNull FreeStmt.Condition condition, @NotNull Consumer<FreeCodeBuilder> thenBlock, @Nullable Consumer<FreeCodeBuilder> elseBlock) {
    var thenBlockBody = subscoped(isBreakable, thenBlock::accept);
    var elseBlockBody = elseBlock == null ? null : subscoped(isBreakable, elseBlock::accept);

    stmts.append(new FreeStmt.IfThenElse(condition, thenBlockBody, elseBlockBody));
  }

  @Override
  public void ifNotTrue(@NotNull LocalVariable notTrue, @NotNull Consumer<FreeCodeBuilder> thenBlock, @Nullable Consumer<FreeCodeBuilder> elseBlock) {
    buildIf(new FreeStmt.Condition.IsFalse(assertFreeVariable(notTrue)), thenBlock, elseBlock);
  }

  @Override
  public void ifTrue(@NotNull LocalVariable theTrue, @NotNull Consumer<FreeCodeBuilder> thenBlock, @Nullable Consumer<FreeCodeBuilder> elseBlock) {
    buildIf(new FreeStmt.Condition.IsTrue(assertFreeVariable(theTrue)), thenBlock, elseBlock);
  }

  @Override
  public void ifInstanceOf(@NotNull FreeJavaExpr lhs, @NotNull ClassDesc rhs, @NotNull BiConsumer<FreeCodeBuilder, LocalVariable> thenBlock, @Nullable Consumer<FreeCodeBuilder> elseBlock) {
    var varHolder = MutableValue.<FreeVariable.Local>create();
    buildIf(new FreeStmt.Condition.IsInstanceOf(assertFreeExpr(lhs), rhs, varHolder), b -> {
      var asTerm = ((FreeCodeBuilderImpl) b).acquireVariable();
      varHolder.set(asTerm);
      thenBlock.accept(b, asTerm);
    }, elseBlock);
  }

  @Override
  public void ifIntEqual(@NotNull FreeJavaExpr lhs, int rhs, @NotNull Consumer<FreeCodeBuilder> thenBlock, @Nullable Consumer<FreeCodeBuilder> elseBlock) {
    buildIf(new FreeStmt.Condition.IsIntEqual(assertFreeExpr(lhs), rhs), thenBlock, elseBlock);
  }

  @Override
  public void ifRefEqual(@NotNull FreeJavaExpr lhs, @NotNull FreeJavaExpr rhs, @NotNull Consumer<FreeCodeBuilder> thenBlock, @Nullable Consumer<FreeCodeBuilder> elseBlock) {
    buildIf(new FreeStmt.Condition.IsRefEqual(assertFreeExpr(lhs), assertFreeExpr(rhs)), thenBlock, elseBlock);
  }

  @Override
  public void ifNull(@NotNull FreeJavaExpr isNull, @NotNull Consumer<FreeCodeBuilder> thenBlock, @Nullable Consumer<FreeCodeBuilder> elseBlock) {
    buildIf(new FreeStmt.Condition.IsNull(assertFreeExpr(isNull)), thenBlock, elseBlock);
  }

  @Override
  public void breakable(@NotNull Consumer<FreeCodeBuilder> innerBlock) {
    var innerBlockBody = subscoped(true, innerBlock::accept);
    stmts.append(new FreeStmt.Breakable(innerBlockBody));
  }

  @Override
  public void breakOut() {
    assert isBreakable;
    stmts.append(FreeStmt.Break.INSTANCE);
  }

  @Override
  public void exec(@NotNull FreeJavaExpr expr) {
    stmts.append(new FreeStmt.Exec(assertFreeExpr(expr)));
  }

  @Override
  public void switchCase(
    @NotNull LocalVariable elim,
    @NotNull ImmutableIntSeq cases,
    @NotNull ObjIntConsumer<FreeCodeBuilder> branch,
    @NotNull Consumer<FreeCodeBuilder> defaultCase
  ) {
    var branchBodies = cases.mapToObj(kase ->
      subscoped(isBreakable, b -> branch.accept(b, kase)));
    var defaultBody = subscoped(isBreakable, defaultCase::accept);

    stmts.append(new FreeStmt.Switch(assertFreeVariable(elim), cases, branchBodies, defaultBody));
  }

  @Override public void returnWith(@NotNull FreeJavaExpr expr) {
    stmts.append(new FreeStmt.Return(assertFreeExpr(expr)));
  }

  @Override public void unreachable() {
    stmts.append(FreeStmt.Unreachable.INSTANCE);
  }

  @Override
  public @NotNull FreeJavaExpr mkNew(@NotNull MethodRef conRef, @NotNull ImmutableSeq<FreeJavaExpr> args) {
    return FreeExprBuilderImpl.INSTANCE.mkNew(conRef, args);
  }

  @Override public @NotNull FreeJavaExpr refVar(@NotNull LocalVariable name) {
    return FreeExprBuilderImpl.INSTANCE.refVar(name);
  }

  @Override
  public @NotNull FreeJavaExpr invoke(@NotNull MethodRef method, @NotNull FreeJavaExpr owner, @NotNull ImmutableSeq<FreeJavaExpr> args) {
    return FreeExprBuilderImpl.INSTANCE.invoke(method, owner, args);
  }

  @Override
  public @NotNull FreeJavaExpr invoke(@NotNull MethodRef method, @NotNull ImmutableSeq<FreeJavaExpr> args) {
    return FreeExprBuilderImpl.INSTANCE.invoke(method, args);
  }

  @Override public @NotNull FreeJavaExpr refField(@NotNull FieldRef field) {
    return FreeExprBuilderImpl.INSTANCE.refField(field);
  }

  @Override
  public @NotNull FreeJavaExpr refField(@NotNull FieldRef field, @NotNull FreeJavaExpr owner) {
    return FreeExprBuilderImpl.INSTANCE.refField(field, owner);
  }

  @Override
  public @NotNull FreeJavaExpr refEnum(@NotNull ClassDesc enumClass, @NotNull String enumName) {
    return FreeExprBuilderImpl.INSTANCE.refEnum(enumClass, enumName);
  }

  @Override
  public @NotNull FreeJavaExpr mkLambda(@NotNull ImmutableSeq<FreeJavaExpr> captures, @NotNull MethodRef method, @NotNull BiConsumer<ArgumentProvider.Lambda, FreeCodeBuilder> builder) {
    return FreeExprBuilderImpl.INSTANCE.mkLambda(captures, method, builder);
  }

  @Override public @NotNull FreeJavaExpr iconst(int i) { return FreeExprBuilderImpl.INSTANCE.iconst(i); }
  @Override public @NotNull FreeJavaExpr iconst(boolean b) { return FreeExprBuilderImpl.INSTANCE.iconst(b); }
  @Override public @NotNull FreeJavaExpr aconst(@NotNull String value) {
    return FreeExprBuilderImpl.INSTANCE.aconst(value);
  }

  @Override public @NotNull FreeJavaExpr aconstNull(@NotNull ClassDesc type) {
    return FreeExprBuilderImpl.INSTANCE.aconstNull(type);
  }

  @Override public @NotNull FreeJavaExpr thisRef() {
    return FreeExprBuilderImpl.INSTANCE.thisRef();
  }

  @Override
  public @NotNull FreeJavaExpr mkArray(@NotNull ClassDesc type, int length, @Nullable ImmutableSeq<FreeJavaExpr> initializer) {
    return FreeExprBuilderImpl.INSTANCE.mkArray(type, length, initializer);
  }

  @Override public @NotNull FreeJavaExpr getArray(@NotNull FreeJavaExpr array, int index) {
    return FreeExprBuilderImpl.INSTANCE.getArray(array, index);
  }

  @Override public @NotNull FreeJavaExpr checkcast(@NotNull FreeJavaExpr obj, @NotNull ClassDesc as) {
    return FreeExprBuilderImpl.INSTANCE.checkcast(obj, as);
  }
}
