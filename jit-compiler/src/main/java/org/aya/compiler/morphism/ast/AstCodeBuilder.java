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
import org.aya.syntax.core.term.Term;
import org.glavo.classfile.ClassHierarchyResolver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.ObjIntConsumer;

public record AstCodeBuilder(
  @NotNull AstClassBuilder owner,
  @NotNull FreezableMutableList<AstStmt> stmts,
  @NotNull VariablePool pool,
  boolean isConstructor,
  boolean isBreakable
) {
  public @NotNull ImmutableSeq<AstStmt> subscoped(boolean isBreakable, @NotNull Consumer<AstCodeBuilder> block) {
    var inner = new AstCodeBuilder(owner, FreezableMutableList.create(), pool.copy(), isConstructor, isBreakable);
    block.accept(inner);
    return inner.build();
  }

  public @NotNull AstVariable.Local acquireVariable() {
    return new AstVariable.Local(pool.acquire());
  }

  public @NotNull ImmutableSeq<AstStmt> build() { return stmts.freeze(); }

  public void invokeSuperCon(@NotNull ImmutableSeq<ClassDesc> superConParams, @NotNull ImmutableSeq<AstValue> superConArgs) {
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

  public void updateArray(@NotNull AstVariable array, int idx, @NotNull AstVariable update) {
    stmts.append(new AstStmt.SetArray(array, idx, update));
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
    // we don't care what type it is
    var isNullVar = bindExpr(ConstantDescs.CD_Object, isNull);
    buildIf(new AstStmt.Condition.IsNull(isNullVar), thenBlock, elseBlock);
  }

  public void breakable(@NotNull Consumer<AstCodeBuilder> innerBlock) {
    var innerBlockBody = subscoped(true, innerBlock);
    stmts.append(new AstStmt.Breakable(innerBlockBody));
  }

  public void breakOut() {
    assert isBreakable;
    stmts.append(AstStmt.SingletonStmt.Break);
  }

  public void whileTrue(@NotNull Consumer<AstCodeBuilder> innerBlock) {
    var innerBlockBody = subscoped(false, innerBlock);
    stmts.append(new AstStmt.WhileTrue(innerBlockBody));
  }

  public void continueLoop() {
    stmts.append(AstStmt.SingletonStmt.Continue);
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

  /// @param expr must have type [Term]
  public void returnWith(@NotNull AstExpr expr) {
    stmts.append(new AstStmt.Return(bindExpr(expr)));
  }

  public void returnWith(@NotNull AstVariable expr) {
    stmts.append(new AstStmt.Return(expr));
  }

  public void unreachable() {
    stmts.append(AstStmt.SingletonStmt.Unreachable);
  }

  public @NotNull AstVariable mkNew(@NotNull MethodRef conRef, @NotNull ImmutableSeq<AstValue> args) {
    return bindExpr(conRef.owner(), new AstExpr.New(conRef, args));
  }

  public void markUsage(@NotNull ClassDesc used, @NotNull ClassHierarchyResolver.ClassHierarchyInfo info) {
    owner.usedClasses().put(used, info);
  }

  /// A `new` expression, the class should have only one (public) constructor with parameter count `args.size()`.
  public @NotNull AstVariable mkNew(@NotNull Class<?> className, @NotNull ImmutableSeq<AstValue> args) {
    var candidates = ImmutableArray.wrap(className.getConstructors())
      .filter(c -> c.getParameterCount() == args.size());

    assert candidates.size() == 1 : "Ambiguous constructors: count " + candidates.size();

    var first = candidates.getFirst();
    var desc = JavaUtil.fromClass(className);
    var conRef = JavaUtil.makeConstructorRef(desc,
      ImmutableArray.wrap(first.getParameterTypes())
        .map(JavaUtil::fromClass));
    return bindExpr(conRef.owner(), new AstExpr.New(conRef, args));
  }

  public @NotNull AstVariable
  invoke(@NotNull MethodRef method, @Nullable AstVariable owner, @NotNull ImmutableSeq<AstValue> args) {
    return bindExpr(method.returnType(), new AstExpr.Invoke(method, owner, args));
  }

  // public @NotNull AstExpr
  // invoke(@NotNull MethodRef method, @NotNull AstVariable owner, @NotNull ImmutableSeq<AstExpr> args) {
  //   return new AstExpr.Invoke(method, owner, bindExprs(args));
  // }

  public @NotNull AstVariable invoke(@NotNull MethodRef method, @NotNull ImmutableSeq<AstValue> args) {
    return bindExpr(method.returnType(), new AstExpr.Invoke(method, null, args));
  }

  public @NotNull AstVariable refField(@NotNull FieldRef field) {
    return bindExpr(field.returnType(), new AstExpr.RefField(field, null));
  }

  // public @NotNull AstExpr refField(@NotNull FieldRef field, @NotNull AstExpr owner) {
  //   // FIXME: type
  //   return new AstExpr.RefField(field, bindExpr(owner));
  // }

  public @NotNull AstVariable refEnum(@NotNull Enum<?> value) {
    var cd = JavaUtil.fromClass(value.getClass());
    var name = value.name();
    return bindExpr(cd, new AstExpr.RefEnum(cd, name));
  }

  public @NotNull AstVariable checkcast(@NotNull AstVariable obj, @NotNull ClassDesc type) {
    return bindExpr(type, new AstExpr.CheckCast(obj, type));
  }

  public @NotNull AstVariable mkLambda(
    @NotNull ImmutableSeq<AstVariable> captures,
    @NotNull MethodRef method,
    @NotNull BiConsumer<AstArgsProvider.Lambda, AstCodeBuilder> builder
  ) {
    var argc = method.paramTypes().size();
    // [0..captures.size()]th parameters are captures
    // [captures.size()..]th parameters are lambda arguments
    // Note that the [VariablePool] counts from 0,
    // as the arguments does NOT count as [local](AstVariable.Local) variables, but instead a [reference to the argument](AstVariable.Arg).
    var lambdaBodyBuilder = new AstCodeBuilder(owner, FreezableMutableList.create(),
      new VariablePool(), false, false);
    builder.accept(new AstArgsProvider.Lambda(captures.size(), argc), lambdaBodyBuilder);
    var lambdaBody = lambdaBodyBuilder.build();

    return bindExpr(method.owner(), new AstExpr.Lambda(captures, method, lambdaBody));
  }

  public @NotNull AstVariable makeArray(@NotNull ClassDesc elementType, int size, @NotNull ImmutableSeq<AstValue> initializer) {
    return bindExpr(elementType.arrayType(), new AstExpr.Array(elementType, size, initializer));
  }

  // public @NotNull AstExpr getArray(@NotNull AstExpr array, int index) {
  //   return new AstExpr.GetArray(bindExpr(array), index);
  // }

  public @NotNull AstVariable bindExpr(@NotNull AstExpr expr) {
    if (expr instanceof AstExpr.Ref(var ref)) return ref;
    if (expr instanceof AstExpr.Const val) return bindExpr(switch (val) {
      case AstExpr.Bconst _ -> ConstantDescs.CD_boolean;
      case AstExpr.Iconst _ -> ConstantDescs.CD_int;
      case AstExpr.Null(var ty) -> ty;
      case AstExpr.Sconst _ -> ConstantDescs.CD_String;
      case AstExpr.This _ -> owner.parentOrThis();
    }, val);
    return bindExpr(Constants.CD_Term, expr);
  }

  public @NotNull AstVariable bindExpr(@NotNull ClassDesc desc, @NotNull AstExpr expr) {
    var astVar = acquireVariable();
    stmts.append(new AstStmt.DeclareVariable(desc, astVar, expr));
    return astVar;
  }
}
