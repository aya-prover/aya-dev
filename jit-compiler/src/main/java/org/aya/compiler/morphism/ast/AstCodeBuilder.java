// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.morphism.ast;

import kala.collection.immutable.ImmutableArray;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.immutable.primitive.ImmutableIntSeq;
import kala.collection.mutable.FreezableMutableList;
import kala.value.MutableValue;
import org.aya.compiler.FieldRef;
import org.aya.compiler.MethodRef;
import org.aya.compiler.morphism.Constants;
import org.aya.compiler.morphism.JavaUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
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
    var inner = new AstCodeBuilder(FreezableMutableList.create(), pool.copy(), isConstructor, isBreakable);
    block.accept(inner);
    return inner.build();
  }

  public @NotNull AstVariable.Local acquireVariable() {
    return new AstVariable.Local(pool.acquire());
  }

  public @NotNull ImmutableSeq<AstStmt> build() { return stmts.freeze(); }

  public void invokeSuperCon(@NotNull ImmutableSeq<ClassDesc> superConParams, @NotNull ImmutableSeq<AstVariable> superConArgs) {
    assert isConstructor;
    assert superConParams.sizeEquals(superConArgs);
    stmts.append(new AstStmt.Super(superConParams, superConArgs));
  }

  public void updateField(@NotNull FieldRef field, @NotNull AstVariable update) {
    stmts.append(new AstStmt.SetStaticField(field, update));
  }
  public void updateVar(@NotNull AstVariable var, @NotNull AstExpr update) {
    stmts.append(new AstStmt.SetVariable(var, update));
  }

  public void updateArray(@NotNull AstExpr array, int idx, @NotNull AstExpr update) {
    var arrVar = bindExpr(array);
    var updateVar = bindExpr(update);
    stmts.append(new AstStmt.SetArray(arrVar, idx, updateVar));
  }

  private void buildIf(@NotNull AstStmt.Condition condition, @NotNull Consumer<AstCodeBuilder> thenBlock, @Nullable Consumer<AstCodeBuilder> elseBlock) {
    var thenBlockBody = subscoped(isBreakable, thenBlock);
    var elseBlockBody = elseBlock == null ? null : subscoped(isBreakable, elseBlock);

    stmts.append(new AstStmt.IfThenElse(condition, thenBlockBody, elseBlockBody));
  }

  public void ifNotTrue(
    @NotNull AstVariable notTrue,
    @NotNull Consumer<AstCodeBuilder> thenBlock,
    @Nullable Consumer<AstCodeBuilder> elseBlock
  ) {
    buildIf(new AstStmt.Condition.IsFalse(notTrue), thenBlock, elseBlock);
  }

  public void ifTrue(
    @NotNull AstVariable theTrue,
    @NotNull Consumer<AstCodeBuilder> thenBlock,
    @Nullable Consumer<AstCodeBuilder> elseBlock
  ) {
    buildIf(new AstStmt.Condition.IsTrue(theTrue), thenBlock, elseBlock);
  }

  public void ifInstanceOf(
    @NotNull AstVariable lhs, @NotNull ClassDesc rhs,
    @NotNull BiConsumer<AstCodeBuilder, AstVariable> thenBlock,
    @Nullable Consumer<AstCodeBuilder> elseBlock
  ) {
    var varHolder = MutableValue.<AstVariable.Local>create();
    buildIf(new AstStmt.Condition.IsInstanceOf(lhs, rhs, varHolder), b -> {
      var asTerm = b.acquireVariable();
      varHolder.set(asTerm);
      thenBlock.accept(b, asTerm);
    }, elseBlock);
  }

  public void ifIntEqual(@NotNull AstVariable lhs, int rhs, @NotNull Consumer<AstCodeBuilder> thenBlock, @Nullable Consumer<AstCodeBuilder> elseBlock) {
    buildIf(new AstStmt.Condition.IsIntEqual(lhs, rhs), thenBlock, elseBlock);
  }

  public void ifRefEqual(@NotNull AstVariable lhs, @NotNull AstVariable rhs, @NotNull Consumer<AstCodeBuilder> thenBlock, @Nullable Consumer<AstCodeBuilder> elseBlock) {
    buildIf(new AstStmt.Condition.IsRefEqual(lhs, rhs), thenBlock, elseBlock);
  }

  public void ifNull(@NotNull AstExpr isNull, @NotNull Consumer<AstCodeBuilder> thenBlock, @Nullable Consumer<AstCodeBuilder> elseBlock) {
    var isNullVar = bindExpr(ConstantDescs.CD_boolean, isNull);
    buildIf(new AstStmt.Condition.IsNull(isNullVar), thenBlock, elseBlock);
  }

  public void breakable(@NotNull Consumer<AstCodeBuilder> innerBlock) {
    var innerBlockBody = subscoped(true, innerBlock);
    stmts.append(new AstStmt.Breakable(innerBlockBody));
  }

  public void breakOut() {
    assert isBreakable;
    stmts.append(AstStmt.Break.INSTANCE);
  }

  public void whileTrue(@NotNull Consumer<AstCodeBuilder> innerBlock) {
    var innerBlockBody = subscoped(false, innerBlock);
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
    var defaultBody = subscoped(isBreakable, defaultCase);

    stmts.append(new AstStmt.Switch(elim, cases, branchBodies, defaultBody));
  }

  public void returnWith(@NotNull AstExpr expr) {
    stmts.append(new AstStmt.Return(bindExpr(expr)));
  }

  public void returnWith(@NotNull AstVariable expr) {
    stmts.append(new AstStmt.Return(expr));
  }

  public void unreachable() {
    stmts.append(AstStmt.Unreachable.INSTANCE);
  }

  public @NotNull AstExpr mkNew(@NotNull MethodRef conRef, @NotNull ImmutableSeq<AstExpr> args) {
    var varArgs = bindExprs(args);
    return new AstExpr.New(conRef, varArgs);
  }

  /// A `new` expression, the class should have only one (public) constructor with parameter count `args.size()`.
  public @NotNull AstVariable mkNew(@NotNull Class<?> className, @NotNull ImmutableSeq<AstVariable> args) {
    var candidates = ImmutableArray.wrap(className.getConstructors())
      .filter(c -> c.getParameterCount() == args.size());

    assert candidates.size() == 1 : "Ambiguous constructors: count " + candidates.size();

    var first = candidates.getFirst();
    var desc = JavaUtil.fromClass(className);
    var conRef = JavaUtil.makeConstructorRef(desc,
      ImmutableArray.wrap(first.getParameterTypes())
        .map(JavaUtil::fromClass));
    return bindExpr(new AstExpr.New(conRef, args));
  }

  public @NotNull AstExpr
  invoke(@NotNull MethodRef method, @NotNull AstExpr owner, @NotNull ImmutableSeq<AstExpr> args) {
    return new AstExpr.Invoke(method, bindExpr(owner), bindExprs(args));
  }

  public @NotNull AstExpr
  invoke(@NotNull MethodRef method, @NotNull AstVariable owner, @NotNull ImmutableSeq<AstExpr> args) {
    return new AstExpr.Invoke(method, owner, bindExprs(args));
  }

  public @NotNull AstExpr invoke(@NotNull MethodRef method, @NotNull ImmutableSeq<AstExpr> args) {
    return new AstExpr.Invoke(method, null, bindExprs(args));
  }

  public @NotNull AstVariable refField(@NotNull FieldRef field) {
    return bindExpr(new AstExpr.RefField(field, null));
  }

  public @NotNull AstExpr refField(@NotNull FieldRef field, @NotNull AstExpr owner) {
    return new AstExpr.RefField(field, bindExpr(owner));
  }

  public @NotNull AstVariable refEnum(@NotNull Enum<?> value) {
    var cd = JavaUtil.fromClass(value.getClass());
    var name = value.name();
    return bindExpr(cd, new AstExpr.RefEnum(cd, name));
  }

  public @NotNull AstVariable iconst(int i) {
    return bindExpr(ConstantDescs.CD_int, new AstExpr.Iconst(i));
  }

  public @NotNull AstVariable aconst(@NotNull String str) {
    return bindExpr(ConstantDescs.CD_String, new AstExpr.Sconst(str));
  }

  public @NotNull AstVariable iconst(boolean b) {
    return bindExpr(ConstantDescs.CD_boolean, new AstExpr.Bconst(b));
  }

  public @NotNull AstVariable thisRef() {
    return bindExpr(AstExpr.This.INSTANCE);
  }

  public @NotNull AstExpr mkLambda(
    @NotNull ImmutableSeq<AstVariable> captures,
    @NotNull MethodRef method,
    @NotNull BiConsumer<AstArgumentProvider.Lambda, AstCodeBuilder> builder
  ) {
    var argc = method.paramTypes().size();
    // [0..captures.size()]th parameters are captures
    // [captures.size()..]th parameters are lambda arguments
    // Note that the [VariablePool] counts from 0,
    // as the arguments does NOT count as [local](AstVariable.Local) variables, but instead a [reference to the argument](AstVariable.Arg).
    var lambdaBodyBuilder = new AstCodeBuilder(FreezableMutableList.create(),
      new VariablePool(), false, false);
    builder.accept(new AstArgumentProvider.Lambda(captures.size(), argc), lambdaBodyBuilder);
    var lambdaBody = lambdaBodyBuilder.build();

    return new AstExpr.Lambda(captures, method, lambdaBody);
  }

  public @NotNull AstExpr getArray(@NotNull AstExpr array, int index) {
    return new AstExpr.GetArray(bindExpr(array), index);
  }

  public @NotNull AstVariable bindExpr(@NotNull AstExpr expr) {
    if (expr instanceof AstExpr.Ref(var ref)) return ref;
    return bindExpr(Constants.CD_Term, expr);
  }

  public @NotNull AstVariable bindExpr(@NotNull ClassDesc desc, @NotNull AstExpr expr) {
    var astVar = acquireVariable();
    stmts.append(new AstStmt.DeclareVariable(desc, astVar));
    stmts.append(new AstStmt.SetVariable(astVar, expr));
    return astVar;
  }

  public @NotNull ImmutableSeq<AstVariable> bindExprs(@NotNull ImmutableSeq<AstExpr> exprs) {
    return exprs.map(this::bindExpr);
  }
}
