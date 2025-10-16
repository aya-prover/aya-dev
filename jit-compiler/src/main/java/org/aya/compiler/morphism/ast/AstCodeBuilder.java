// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.morphism.ast;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.immutable.primitive.ImmutableIntSeq;
import kala.collection.mutable.FreezableMutableList;
import kala.value.MutableValue;
import org.aya.compiler.FieldRef;
import org.aya.compiler.MethodRef;
import org.aya.compiler.morphism.ArgumentProvider;
import org.aya.compiler.morphism.Constants;
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
) {
  public @NotNull ImmutableSeq<AstStmt> subscoped(boolean isBreakable, @NotNull Consumer<AstCodeBuilder> block) {
    var inner = new AstCodeBuilder(FreezableMutableList.create(), pool, isConstructor, isBreakable);
    block.accept(inner);
    return inner.build();
  }

  public @NotNull AstVariable.Local acquireVariable() {
    return new AstVariable.Local(pool.acquire());
  }

  public @NotNull ImmutableSeq<AstStmt> build() { return stmts.freeze(); }

  public void invokeSuperCon(@NotNull ImmutableSeq<ClassDesc> superConParams, @NotNull ImmutableSeq<AstExpr> superConArgs) {
    assert isConstructor;
    assert superConParams.sizeEquals(superConArgs);
    var argVars = bindExprs(superConArgs);
    stmts.append(new AstStmt.Super(superConParams, argVars));
  }

  public void updateVar(@NotNull AstVariable var, @NotNull AstExpr update) {
    stmts.append(new AstStmt.SetVariable(var, update));
  }

  public void updateArray(@NotNull AstExpr array, int idx, @NotNull AstExpr update) {
    stmts.append(new AstStmt.SetArray(array, idx, update));
  }

  private void buildIf(@NotNull AstStmt.Condition condition, @NotNull Consumer<AstCodeBuilder> thenBlock, @Nullable Consumer<AstCodeBuilder> elseBlock) {
    var thenBlockBody = subscoped(isBreakable, thenBlock::accept);
    var elseBlockBody = elseBlock == null ? null : subscoped(isBreakable, elseBlock::accept);

    stmts.append(new AstStmt.IfThenElse(condition, thenBlockBody, elseBlockBody));
  }

  public void
  ifNotTrue(@NotNull AstVariable notTrue, @NotNull Consumer<AstCodeBuilder> thenBlock, @Nullable Consumer<AstCodeBuilder> elseBlock) {
    buildIf(new AstStmt.Condition.IsFalse(notTrue), thenBlock, elseBlock);
  }

  public void
  ifTrue(@NotNull AstVariable theTrue, @NotNull Consumer<AstCodeBuilder> thenBlock, @Nullable Consumer<AstCodeBuilder> elseBlock) {
    buildIf(new AstStmt.Condition.IsTrue(theTrue), thenBlock, elseBlock);
  }

  public void
  ifInstanceOf(@NotNull AstExpr lhs, @NotNull ClassDesc rhs, @NotNull BiConsumer<AstCodeBuilder, AstVariable> thenBlock, @Nullable Consumer<AstCodeBuilder> elseBlock) {
    var varHolder = MutableValue.<AstVariable.Local>create();
    buildIf(new AstStmt.Condition.IsInstanceOf(lhs, rhs, varHolder), b -> {
      var asTerm = b.acquireVariable();
      varHolder.set(asTerm);
      thenBlock.accept(b, asTerm);
    }, elseBlock);
  }

  public void ifIntEqual(@NotNull AstExpr lhs, int rhs, @NotNull Consumer<AstCodeBuilder> thenBlock, @Nullable Consumer<AstCodeBuilder> elseBlock) {
    buildIf(new AstStmt.Condition.IsIntEqual(lhs, rhs), thenBlock, elseBlock);
  }

  public void ifRefEqual(@NotNull AstExpr lhs, @NotNull AstExpr rhs, @NotNull Consumer<AstCodeBuilder> thenBlock, @Nullable Consumer<AstCodeBuilder> elseBlock) {
    buildIf(new AstStmt.Condition.IsRefEqual(lhs, rhs), thenBlock, elseBlock);
  }

  public void ifNull(@NotNull AstExpr isNull, @NotNull Consumer<AstCodeBuilder> thenBlock, @Nullable Consumer<AstCodeBuilder> elseBlock) {
    buildIf(new AstStmt.Condition.IsNull(isNull), thenBlock, elseBlock);
  }

  public void breakable(@NotNull Consumer<AstCodeBuilder> innerBlock) {
    var innerBlockBody = subscoped(true, innerBlock::accept);
    stmts.append(new AstStmt.Breakable(innerBlockBody));
  }

  public void breakOut() {
    assert isBreakable;
    stmts.append(AstStmt.Break.INSTANCE);
  }

  public void whileTrue(@NotNull Consumer<AstCodeBuilder> innerBlock) {
    var innerBlockBody = subscoped(false, innerBlock::accept);
    stmts.append(new AstStmt.WhileTrue(innerBlockBody));
  }

  public void continueLoop() {
    stmts.append(AstStmt.Continue.INSTANCE);
  }

  public void exec(@NotNull AstExpr expr) {
    stmts.append(new AstStmt.Exec(expr));
  }

  public void switchCase(
    @NotNull AstVariable elim,
    @NotNull ImmutableIntSeq cases,
    @NotNull ObjIntConsumer<AstCodeBuilder> branch,
    @NotNull Consumer<AstCodeBuilder> defaultCase
  ) {
    var branchBodies = cases.mapToObj(kase ->
      subscoped(isBreakable, b -> branch.accept(b, kase)));
    var defaultBody = subscoped(isBreakable, defaultCase::accept);

    stmts.append(new AstStmt.Switch(elim, cases, branchBodies, defaultBody));
  }

  public void returnWith(@NotNull AstExpr expr) {
    stmts.append(new AstStmt.Return(expr));
  }

  public void unreachable() {
    stmts.append(AstStmt.Unreachable.INSTANCE);
  }

  public @NotNull AstExpr mkNew(@NotNull MethodRef conRef, @NotNull ImmutableSeq<AstExpr> args) {
    var varArgs = bindExprs(args);
    return new AstExpr.New(conRef, varArgs);
  }

  public @NotNull AstExpr refVar(@NotNull AstVariable name) {
    return new AstExpr.RefVariable(name);
  }

  public @NotNull AstExpr
  invoke(@NotNull MethodRef method, @NotNull AstExpr owner, @NotNull ImmutableSeq<AstExpr> args) {
    return new AstExpr.Invoke(method, bindExpr(owner), bindExprs(args));
  }

  public @NotNull AstExpr invoke(@NotNull MethodRef method, @NotNull ImmutableSeq<AstExpr> args) {
    return new AstExpr.Invoke(method, null, bindExprs(args));
  }

  public @NotNull AstExpr refField(@NotNull FieldRef field) {
    return new AstExpr.RefField(field, null);
  }

  public @NotNull AstExpr refField(@NotNull FieldRef field, @NotNull AstExpr owner) {
    return new AstExpr.RefField(field, bindExpr(owner));
  }

  public @NotNull AstExpr refEnum(@NotNull ClassDesc enumClass, @NotNull String enumName) {
    return new AstExpr.RefEnum(enumClass, enumName);
  }

  public @NotNull AstExpr mkLambda(
    @NotNull ImmutableSeq<AstExpr> captures,
    @NotNull MethodRef method,
    @NotNull BiConsumer<ArgumentProvider.Lambda, AstCodeBuilder> builder
  ) {
    var varCaptures = bindExprs(captures);
    var argc = method.paramTypes().size();
    // [0..captures.size()]th parameters are captures
    // [captures.size()..]th parameters are lambda arguments
    // Note that the [VariablePool] counts from 0,
    // as the arguments does NOT count as [local](AstVariable.Local) variables, but instead a [reference to the argument](AstVariable.Arg).
    var lambdaBodyBuilder = new AstCodeBuilder(FreezableMutableList.create(),
      new VariablePool(), false, false);
    builder.accept(new AstArgumentProvider.Lambda(varCaptures.size(), argc), lambdaBodyBuilder);
    var lambdaBody = lambdaBodyBuilder.build();

    return new AstExpr.Lambda(varCaptures, method, lambdaBody);
  }

  public @NotNull AstExpr iconst(int i) { return _AstExprBuilder.INSTANCE.iconst(i); }
  public @NotNull AstExpr iconst(boolean b) { return _AstExprBuilder.INSTANCE.iconst(b); }
  public @NotNull AstExpr aconst(@NotNull String value) {
    return _AstExprBuilder.INSTANCE.aconst(value);
  }

  public @NotNull AstExpr aconstNull(@NotNull ClassDesc type) {
    return _AstExprBuilder.INSTANCE.aconstNull(type);
  }

  public @NotNull AstExpr thisRef() { return _AstExprBuilder.INSTANCE.thisRef(); }

  public @NotNull AstExpr
  mkArray(@NotNull ClassDesc type, int length, @Nullable ImmutableSeq<AstExpr> initializer) {
    return new AstExpr.Array(type, length, initializer == null ? null : bindExprs(initializer));
  }

  public @NotNull AstExpr getArray(@NotNull AstExpr array, int index) {
    return new AstExpr.GetArray(bindExpr(array), index);
  }

  public @NotNull AstExpr checkcast(@NotNull AstExpr obj, @NotNull ClassDesc as) {
    return _AstExprBuilder.INSTANCE.checkcast(obj, as);
  }

  public @NotNull AstVariable bindExpr(@NotNull AstExpr expr) {
    // TODO: check for single variable expr & prevent adding another layer
    var index = pool.acquire();
    var astVar = new AstVariable.Local(index);
    stmts.append(new AstStmt.DeclareVariable(Constants.CD_Term, astVar));
    stmts.append(new AstStmt.SetVariable(astVar, expr));
    return astVar;
  }

  public @NotNull ImmutableSeq<AstVariable> bindExprs(@NotNull ImmutableSeq<AstExpr> exprs) {
    return exprs.map(this::bindExpr);
  }
}
