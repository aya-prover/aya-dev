// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.morphism.ast;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.immutable.primitive.ImmutableIntSeq;
import kala.collection.mutable.FreezableMutableList;
import kala.value.MutableValue;
import org.aya.compiler.FieldRef;
import org.aya.compiler.LocalVariable;
import org.aya.compiler.MethodRef;
import org.aya.compiler.morphism.ArgumentProvider;
import org.aya.compiler.morphism.CodeBuilder;
import org.aya.compiler.morphism.JavaExpr;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.constant.ClassDesc;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.ObjIntConsumer;

public record AstCodeBuilder(
  @NotNull FreezableMutableList<AstStmt> stmts,
  @NotNull VariablePool pool,
  boolean isConstructor,
  boolean isBreakable
) implements CodeBuilder {
  public @NotNull ImmutableSeq<AstStmt> subscoped(boolean isBreakable, @NotNull Consumer<AstCodeBuilder> block) {
    var inner = new AstCodeBuilder(FreezableMutableList.create(), pool, isConstructor, isBreakable);
    block.accept(inner);
    return inner.build();
  }

  public @NotNull ImmutableSeq<AstStmt> build() { return stmts.freeze(); }
  public static @NotNull AstExpr assertFreeExpr(@NotNull JavaExpr expr) { return (AstExpr) expr; }

  public static @NotNull ImmutableSeq<AstExpr> assertFreeExpr(@NotNull ImmutableSeq<JavaExpr> exprs) {
    return exprs.map(x -> (AstExpr) x);
  }

  public static @NotNull AstVariable assertFreeVariable(@NotNull LocalVariable var) { return (AstVariable) var; }
  public @NotNull AstVariable.Local acquireVariable() { return new AstVariable.Local(pool.acquire()); }

  @Override public @NotNull AstVariable makeVar(@NotNull ClassDesc type, @Nullable AstExpr initializer) {
    var theVar = acquireVariable();
    stmts.append(new AstStmt.DeclareVariable(type, theVar));
    if (initializer != null) updateVar(theVar, initializer);
    return theVar;
  }

  @Override
  public void invokeSuperCon(@NotNull ImmutableSeq<ClassDesc> superConParams, @NotNull ImmutableSeq<JavaExpr> superConArgs) {
    assert isConstructor;
    assert superConParams.sizeEquals(superConArgs);
    stmts.append(new AstStmt.Super(superConParams, assertFreeExpr(superConArgs)));
  }

  @Override public void updateVar(@NotNull LocalVariable var, @NotNull JavaExpr update) {
    stmts.append(new AstStmt.SetVariable(assertFreeVariable(var), assertFreeExpr(update)));
  }

  @Override public void updateArray(@NotNull JavaExpr array, int idx, @NotNull JavaExpr update) {
    stmts.append(new AstStmt.SetArray(assertFreeExpr(array), idx, assertFreeExpr(update)));
  }

  private void buildIf(@NotNull AstStmt.Condition condition, @NotNull Consumer<CodeBuilder> thenBlock, @Nullable Consumer<CodeBuilder> elseBlock) {
    var thenBlockBody = subscoped(isBreakable, thenBlock::accept);
    var elseBlockBody = elseBlock == null ? null : subscoped(isBreakable, elseBlock::accept);

    stmts.append(new AstStmt.IfThenElse(condition, thenBlockBody, elseBlockBody));
  }

  @Override public void
  ifNotTrue(@NotNull LocalVariable notTrue, @NotNull Consumer<CodeBuilder> thenBlock, @Nullable Consumer<CodeBuilder> elseBlock) {
    buildIf(new AstStmt.Condition.IsFalse(assertFreeVariable(notTrue)), thenBlock, elseBlock);
  }

  @Override public void
  ifTrue(@NotNull LocalVariable theTrue, @NotNull Consumer<CodeBuilder> thenBlock, @Nullable Consumer<CodeBuilder> elseBlock) {
    buildIf(new AstStmt.Condition.IsTrue(assertFreeVariable(theTrue)), thenBlock, elseBlock);
  }

  @Override public void
  ifInstanceOf(@NotNull JavaExpr lhs, @NotNull ClassDesc rhs, @NotNull BiConsumer<CodeBuilder, LocalVariable> thenBlock, @Nullable Consumer<CodeBuilder> elseBlock) {
    var varHolder = MutableValue.<AstVariable.Local>create();
    buildIf(new AstStmt.Condition.IsInstanceOf(assertFreeExpr(lhs), rhs, varHolder), b -> {
      var asTerm = ((AstCodeBuilder) b).acquireVariable();
      varHolder.set(asTerm);
      thenBlock.accept(b, asTerm);
    }, elseBlock);
  }

  @Override
  public void ifIntEqual(@NotNull JavaExpr lhs, int rhs, @NotNull Consumer<CodeBuilder> thenBlock, @Nullable Consumer<CodeBuilder> elseBlock) {
    buildIf(new AstStmt.Condition.IsIntEqual(assertFreeExpr(lhs), rhs), thenBlock, elseBlock);
  }

  @Override
  public void ifRefEqual(@NotNull JavaExpr lhs, @NotNull JavaExpr rhs, @NotNull Consumer<CodeBuilder> thenBlock, @Nullable Consumer<CodeBuilder> elseBlock) {
    buildIf(new AstStmt.Condition.IsRefEqual(assertFreeExpr(lhs), assertFreeExpr(rhs)), thenBlock, elseBlock);
  }

  @Override
  public void ifNull(@NotNull JavaExpr isNull, @NotNull Consumer<CodeBuilder> thenBlock, @Nullable Consumer<CodeBuilder> elseBlock) {
    buildIf(new AstStmt.Condition.IsNull(assertFreeExpr(isNull)), thenBlock, elseBlock);
  }

  @Override public void breakable(@NotNull Consumer<CodeBuilder> innerBlock) {
    var innerBlockBody = subscoped(true, innerBlock::accept);
    stmts.append(new AstStmt.Breakable(innerBlockBody));
  }

  @Override public void breakOut() {
    assert isBreakable;
    stmts.append(AstStmt.Break.INSTANCE);
  }

  @Override public void whileTrue(@NotNull Consumer<CodeBuilder> innerBlock) {
    var innerBlockBody = subscoped(false, innerBlock::accept);
    stmts.append(new AstStmt.WhileTrue(innerBlockBody));
  }

  @Override public void continueLoop() {
    stmts.append(AstStmt.Continue.INSTANCE);
  }

  @Override public void exec(@NotNull JavaExpr expr) {
    stmts.append(new AstStmt.Exec(assertFreeExpr(expr)));
  }

  @Override public void switchCase(
    @NotNull LocalVariable elim,
    @NotNull ImmutableIntSeq cases,
    @NotNull ObjIntConsumer<CodeBuilder> branch,
    @NotNull Consumer<CodeBuilder> defaultCase
  ) {
    var branchBodies = cases.mapToObj(kase ->
      subscoped(isBreakable, b -> branch.accept(b, kase)));
    var defaultBody = subscoped(isBreakable, defaultCase::accept);

    stmts.append(new AstStmt.Switch(assertFreeVariable(elim), cases, branchBodies, defaultBody));
  }

  @Override public void returnWith(@NotNull JavaExpr expr) {
    stmts.append(new AstStmt.Return(assertFreeExpr(expr)));
  }

  @Override public void unreachable() {
    stmts.append(AstStmt.Unreachable.INSTANCE);
  }

  @Override public @NotNull JavaExpr mkNew(@NotNull MethodRef conRef, @NotNull ImmutableSeq<JavaExpr> args) {
    return _AstExprBuilder.INSTANCE.mkNew(conRef, args);
  }

  @Override public @NotNull JavaExpr refVar(@NotNull LocalVariable name) {
    return _AstExprBuilder.INSTANCE.refVar(name);
  }

  @Override public @NotNull JavaExpr
  invoke(@NotNull MethodRef method, @NotNull JavaExpr owner, @NotNull ImmutableSeq<JavaExpr> args) {
    return _AstExprBuilder.INSTANCE.invoke(method, owner, args);
  }

  @Override public @NotNull JavaExpr invoke(@NotNull MethodRef method, @NotNull ImmutableSeq<JavaExpr> args) {
    return _AstExprBuilder.INSTANCE.invoke(method, args);
  }

  @Override public @NotNull JavaExpr refField(@NotNull FieldRef field) {
    return _AstExprBuilder.INSTANCE.refField(field);
  }

  @Override public @NotNull JavaExpr refField(@NotNull FieldRef field, @NotNull JavaExpr owner) {
    return _AstExprBuilder.INSTANCE.refField(field, owner);
  }

  @Override public @NotNull JavaExpr refEnum(@NotNull ClassDesc enumClass, @NotNull String enumName) {
    return _AstExprBuilder.INSTANCE.refEnum(enumClass, enumName);
  }

  @Override
  public @NotNull JavaExpr mkLambda(@NotNull ImmutableSeq<JavaExpr> captures, @NotNull MethodRef method, @NotNull BiConsumer<ArgumentProvider.Lambda, CodeBuilder> builder) {
    return _AstExprBuilder.INSTANCE.mkLambda(captures, method, builder);
  }

  @Override public @NotNull JavaExpr iconst(int i) { return _AstExprBuilder.INSTANCE.iconst(i); }
  @Override public @NotNull JavaExpr iconst(boolean b) { return _AstExprBuilder.INSTANCE.iconst(b); }
  @Override public @NotNull JavaExpr aconst(@NotNull String value) {
    return _AstExprBuilder.INSTANCE.aconst(value);
  }

  @Override public @NotNull JavaExpr aconstNull(@NotNull ClassDesc type) {
    return _AstExprBuilder.INSTANCE.aconstNull(type);
  }

  @Override public @NotNull JavaExpr thisRef() { return _AstExprBuilder.INSTANCE.thisRef(); }

  @Override public @NotNull JavaExpr
  mkArray(@NotNull ClassDesc type, int length, @Nullable ImmutableSeq<JavaExpr> initializer) {
    return _AstExprBuilder.INSTANCE.mkArray(type, length, initializer);
  }

  @Override public @NotNull JavaExpr getArray(@NotNull JavaExpr array, int index) {
    return _AstExprBuilder.INSTANCE.getArray(array, index);
  }

  @Override public @NotNull JavaExpr checkcast(@NotNull JavaExpr obj, @NotNull ClassDesc as) {
    return _AstExprBuilder.INSTANCE.checkcast(obj, as);
  }
}
