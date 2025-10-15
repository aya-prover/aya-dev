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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.constant.ClassDesc;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.ObjIntConsumer;

public class AstCodeBuilder(
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
  public static @NotNull AstExpr assertFreeExpr(@NotNull AstExpr expr) { return (AstExpr) expr; }

  public static @NotNull ImmutableSeq<AstExpr> assertFreeExpr(@NotNull ImmutableSeq<AstExpr> exprs) {
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
  public void invokeSuperCon(@NotNull ImmutableSeq<ClassDesc> superConParams, @NotNull ImmutableSeq<AstExpr> superConArgs) {
    assert isConstructor;
    assert superConParams.sizeEquals(superConArgs);
    stmts.append(new AstStmt.Super(superConParams, assertFreeExpr(superConArgs)));
  }

  @Override public void updateVar(@NotNull AstVariable var, @NotNull AstExpr update) {
    stmts.append(new AstStmt.SetVariable(assertFreeVariable(var), assertFreeExpr(update)));
  }

  @Override public void updateArray(@NotNull AstExpr array, int idx, @NotNull AstExpr update) {
    stmts.append(new AstStmt.SetArray(assertFreeExpr(array), idx, assertFreeExpr(update)));
  }

  private void buildIf(@NotNull AstStmt.Condition condition, @NotNull Consumer<CodeBuilder> thenBlock, @Nullable Consumer<CodeBuilder> elseBlock) {
    var thenBlockBody = subscoped(isBreakable, thenBlock::accept);
    var elseBlockBody = elseBlock == null ? null : subscoped(isBreakable, elseBlock::accept);

    stmts.append(new AstStmt.IfThenElse(condition, thenBlockBody, elseBlockBody));
  }

  @Override public void
  ifNotTrue(@NotNull AstVariable notTrue, @NotNull Consumer<CodeBuilder> thenBlock, @Nullable Consumer<CodeBuilder> elseBlock) {
    buildIf(new AstStmt.Condition.IsFalse(assertFreeVariable(notTrue)), thenBlock, elseBlock);
  }

  @Override public void
  ifTrue(@NotNull AstVariable theTrue, @NotNull Consumer<CodeBuilder> thenBlock, @Nullable Consumer<CodeBuilder> elseBlock) {
    buildIf(new AstStmt.Condition.IsTrue(assertFreeVariable(theTrue)), thenBlock, elseBlock);
  }

  @Override public void
  ifInstanceOf(@NotNull AstExpr lhs, @NotNull ClassDesc rhs, @NotNull BiConsumer<CodeBuilder, AstVariable> thenBlock, @Nullable Consumer<CodeBuilder> elseBlock) {
    var varHolder = MutableValue.<AstVariable.Local>create();
    buildIf(new AstStmt.Condition.IsInstanceOf(assertFreeExpr(lhs), rhs, varHolder), b -> {
      var asTerm = ((AstCodeBuilder) b).acquireVariable();
      varHolder.set(asTerm);
      thenBlock.accept(b, asTerm);
    }, elseBlock);
  }

  @Override
  public void ifIntEqual(@NotNull AstExpr lhs, int rhs, @NotNull Consumer<CodeBuilder> thenBlock, @Nullable Consumer<CodeBuilder> elseBlock) {
    buildIf(new AstStmt.Condition.IsIntEqual(assertFreeExpr(lhs), rhs), thenBlock, elseBlock);
  }

  @Override
  public void ifRefEqual(@NotNull AstExpr lhs, @NotNull AstExpr rhs, @NotNull Consumer<CodeBuilder> thenBlock, @Nullable Consumer<CodeBuilder> elseBlock) {
    buildIf(new AstStmt.Condition.IsRefEqual(assertFreeExpr(lhs), assertFreeExpr(rhs)), thenBlock, elseBlock);
  }

  @Override
  public void ifNull(@NotNull AstExpr isNull, @NotNull Consumer<CodeBuilder> thenBlock, @Nullable Consumer<CodeBuilder> elseBlock) {
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

  @Override public void exec(@NotNull AstExpr expr) {
    stmts.append(new AstStmt.Exec(assertFreeExpr(expr)));
  }

  @Override public void switchCase(
    @NotNull AstVariable elim,
    @NotNull ImmutableIntSeq cases,
    @NotNull ObjIntConsumer<CodeBuilder> branch,
    @NotNull Consumer<CodeBuilder> defaultCase
  ) {
    var branchBodies = cases.mapToObj(kase ->
      subscoped(isBreakable, b -> branch.accept(b, kase)));
    var defaultBody = subscoped(isBreakable, defaultCase::accept);

    stmts.append(new AstStmt.Switch(assertFreeVariable(elim), cases, branchBodies, defaultBody));
  }

  @Override public void returnWith(@NotNull AstExpr expr) {
    stmts.append(new AstStmt.Return(assertFreeExpr(expr)));
  }

  @Override public void unreachable() {
    stmts.append(AstStmt.Unreachable.INSTANCE);
  }

  @Override public @NotNull AstExpr mkNew(@NotNull MethodRef conRef, @NotNull ImmutableSeq<AstExpr> args) {
    // TODO: update context
    return new AstExpr.New(conRef, args);
  }

  @Override public @NotNull AstExpr refVar(@NotNull AstVariable name) {
    return new AstExpr.RefVariable(name);
  }

  @Override public @NotNull AstExpr
  invoke(@NotNull MethodRef method, @NotNull AstExpr owner, @NotNull ImmutableSeq<AstExpr> args) {
    return _AstExprBuilder.INSTANCE.invoke(method, owner, args);
  }

  @Override public @NotNull AstExpr invoke(@NotNull MethodRef method, @NotNull ImmutableSeq<AstExpr> args) {
    return _AstExprBuilder.INSTANCE.invoke(method, args);
  }

  @Override public @NotNull AstExpr refField(@NotNull FieldRef field) {
    return _AstExprBuilder.INSTANCE.refField(field);
  }

  @Override public @NotNull AstExpr refField(@NotNull FieldRef field, @NotNull AstExpr owner) {
    return _AstExprBuilder.INSTANCE.refField(field, owner);
  }

  @Override public @NotNull AstExpr refEnum(@NotNull ClassDesc enumClass, @NotNull String enumName) {
    return _AstExprBuilder.INSTANCE.refEnum(enumClass, enumName);
  }

  @Override
  public @NotNull AstExpr mkLambda(@NotNull ImmutableSeq<AstExpr> captures, @NotNull MethodRef method, @NotNull BiConsumer<ArgumentProvider.Lambda, CodeBuilder> builder) {
    return _AstExprBuilder.INSTANCE.mkLambda(captures, method, builder);
  }

  @Override public @NotNull AstExpr iconst(int i) { return _AstExprBuilder.INSTANCE.iconst(i); }
  @Override public @NotNull AstExpr iconst(boolean b) { return _AstExprBuilder.INSTANCE.iconst(b); }
  @Override public @NotNull AstExpr aconst(@NotNull String value) {
    return _AstExprBuilder.INSTANCE.aconst(value);
  }

  @Override public @NotNull AstExpr aconstNull(@NotNull ClassDesc type) {
    return _AstExprBuilder.INSTANCE.aconstNull(type);
  }

  @Override public @NotNull AstExpr thisRef() { return _AstExprBuilder.INSTANCE.thisRef(); }

  @Override public @NotNull AstExpr
  mkArray(@NotNull ClassDesc type, int length, @Nullable ImmutableSeq<AstExpr> initializer) {
    return new AstExpr.Array(type, length, initializer == null ? null : assertFreeExpr(initializer));
  }

  @Override public @NotNull AstExpr getArray(@NotNull AstExpr array, int index) {
    return new AstExpr.GetArray(array, index);
  }

  @Override public @NotNull AstExpr checkcast(@NotNull AstExpr obj, @NotNull ClassDesc as) {
    return _AstExprBuilder.INSTANCE.checkcast(obj, as);
  }

  public @NotNull AstVariable bindExpr(@NotNull AstExpr expr) {
    // var index = pool.acquire();
    // stmts.append();
    return null;
  }

  public @NotNull ImmutableSeq<AstVariable> bindExprs(@NotNull ImmutableSeq<AstExpr> exprs) {
    // exprs.map(expr -> {
    //   var index = pool.acquire();
    // });
    return null;
  }
}
