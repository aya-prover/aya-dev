// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.morphism.ir;

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

public record IrCodeBuilder(
  @NotNull IrClassBuilder owner,
  @NotNull FreezableMutableList<IrStmt> stmts,
  @NotNull VariablePool pool,
  boolean isConstructor,
  boolean isBreakable
) {
  public @NotNull ImmutableSeq<IrStmt> subscoped(boolean isBreakable, @NotNull Consumer<IrCodeBuilder> block) {
    var inner = new IrCodeBuilder(owner, FreezableMutableList.create(), pool.copy(), isConstructor, isBreakable);
    block.accept(inner);
    return inner.build();
  }

  public @NotNull IrVariable.Local acquireVariable() {
    return new IrVariable.Local(pool.acquire());
  }

  public @NotNull ImmutableSeq<IrStmt> build() { return stmts.freeze(); }

  public void invokeSuperCon(@NotNull ImmutableSeq<ClassDesc> superConParams, @NotNull ImmutableSeq<IrValue> superConArgs) {
    assert isConstructor;
    assert superConParams.sizeEquals(superConArgs);
    stmts.append(new IrStmt.Super(superConParams, superConArgs));
  }

  public void updateField(@NotNull FieldRef field, @NotNull IrVariable update) {
    stmts.append(new IrStmt.SetStaticField(field, update));
  }
  public void updateVar(@NotNull IrVariable var, @NotNull IrExpr update) {
    stmts.append(new IrStmt.SetVariable(var, update));
  }

  public void updateArray(@NotNull IrVariable array, int idx, @NotNull IrVariable update) {
    stmts.append(new IrStmt.SetArray(array, idx, update));
  }

  private void buildIf(@NotNull IrStmt.Condition condition, @NotNull Consumer<IrCodeBuilder> thenBlock, @Nullable Consumer<IrCodeBuilder> elseBlock) {
    var thenBlockBody = subscoped(isBreakable, thenBlock);
    var elseBlockBody = elseBlock == null ? null : subscoped(isBreakable, elseBlock);

    stmts.append(new IrStmt.IfThenElse(condition, thenBlockBody, elseBlockBody));
  }

  public void ifNotTrue(
    @NotNull IrVariable notTrue,
    @NotNull Consumer<IrCodeBuilder> thenBlock,
    @Nullable Consumer<IrCodeBuilder> elseBlock
  ) {
    buildIf(new IrStmt.Condition.IsFalse(notTrue), thenBlock, elseBlock);
  }

  public void ifTrue(
    @NotNull IrVariable theTrue,
    @NotNull Consumer<IrCodeBuilder> thenBlock,
    @Nullable Consumer<IrCodeBuilder> elseBlock
  ) {
    buildIf(new IrStmt.Condition.IsTrue(theTrue), thenBlock, elseBlock);
  }

  public void ifInstanceOf(
    @NotNull IrVariable lhs, @NotNull ClassDesc rhs,
    @NotNull BiConsumer<IrCodeBuilder, IrVariable> thenBlock,
    @Nullable Consumer<IrCodeBuilder> elseBlock
  ) {
    var varHolder = MutableValue.<IrVariable.Local>create();
    buildIf(new IrStmt.Condition.IsInstanceOf(lhs, rhs, varHolder), b -> {
      var asTerm = b.acquireVariable();
      varHolder.set(asTerm);
      thenBlock.accept(b, asTerm);
    }, elseBlock);
  }

  public void ifIntEqual(@NotNull IrVariable lhs, int rhs, @NotNull Consumer<IrCodeBuilder> thenBlock, @Nullable Consumer<IrCodeBuilder> elseBlock) {
    buildIf(new IrStmt.Condition.IsIntEqual(lhs, rhs), thenBlock, elseBlock);
  }

  public void ifRefEqual(@NotNull IrVariable lhs, @NotNull IrVariable rhs, @NotNull Consumer<IrCodeBuilder> thenBlock, @Nullable Consumer<IrCodeBuilder> elseBlock) {
    buildIf(new IrStmt.Condition.IsRefEqual(lhs, rhs), thenBlock, elseBlock);
  }

  public void ifNull(@NotNull IrExpr isNull, @NotNull Consumer<IrCodeBuilder> thenBlock, @Nullable Consumer<IrCodeBuilder> elseBlock) {
    // we don't care what type it is
    var isNullVar = bindExpr(ConstantDescs.CD_Object, isNull);
    buildIf(new IrStmt.Condition.IsNull(isNullVar), thenBlock, elseBlock);
  }

  public void breakable(@NotNull Consumer<IrCodeBuilder> innerBlock) {
    var innerBlockBody = subscoped(true, innerBlock);
    stmts.append(new IrStmt.Breakable(innerBlockBody));
  }

  public void breakOut() {
    assert isBreakable;
    stmts.append(IrStmt.SingletonStmt.Break);
  }

  public void whileTrue(@NotNull Consumer<IrCodeBuilder> innerBlock) {
    var innerBlockBody = subscoped(false, innerBlock);
    stmts.append(new IrStmt.WhileTrue(innerBlockBody));
  }

  public void continueLoop() {
    stmts.append(IrStmt.SingletonStmt.Continue);
  }

  public void exec(@NotNull IrExpr expr) {
    stmts.append(new IrStmt.Exec(expr));
  }

  public void switchCase(
    @NotNull IrVariable elim,
    @NotNull ImmutableIntSeq cases,
    @NotNull ObjIntConsumer<IrCodeBuilder> branch,
    @NotNull Consumer<IrCodeBuilder> defaultCase
  ) {
    var branchBodies = cases.mapToObj(kase ->
      subscoped(isBreakable, b -> branch.accept(b, kase)));
    var defaultBody = subscoped(isBreakable, defaultCase);

    stmts.append(new IrStmt.Switch(elim, cases, branchBodies, defaultBody));
  }

  /// @param expr must have type [Term]
  public void returnWith(@NotNull IrExpr expr) {
    returnWith(bindExpr(expr));
  }

  public void returnWith(@NotNull IrVariable expr) {
    stmts.append(new IrStmt.Return(expr));
  }

  public void unreachable() {
    stmts.append(IrStmt.SingletonStmt.Unreachable);
  }

  public @NotNull IrVariable mkNew(@NotNull MethodRef conRef, @NotNull ImmutableSeq<IrValue> args) {
    return bindExpr(conRef.owner(), new IrExpr.New(conRef, args));
  }

  public void markUsage(@NotNull ClassDesc used, @NotNull ClassHierarchyResolver.ClassHierarchyInfo info) {
    owner.usedClasses().put(used, info);
  }

  /// A `new` expression, the class should have only one (public) constructor with parameter count `args.size()`.
  public @NotNull IrVariable mkNew(@NotNull Class<?> className, @NotNull ImmutableSeq<IrValue> args) {
    var candidates = ImmutableArray.wrap(className.getConstructors())
      .filter(c -> c.getParameterCount() == args.size());

    assert candidates.size() == 1 : "Ambiguous constructors: count " + candidates.size();

    var first = candidates.getFirst();
    var desc = JavaUtil.fromClass(className);
    var conRef = JavaUtil.makeConstructorRef(desc,
      ImmutableArray.wrap(first.getParameterTypes())
        .map(JavaUtil::fromClass));
    return bindExpr(conRef.owner(), new IrExpr.New(conRef, args));
  }

  public @NotNull IrVariable
  invoke(@NotNull MethodRef method, @Nullable IrVariable owner, @NotNull ImmutableSeq<IrValue> args) {
    return bindExpr(method.returnType(), new IrExpr.Invoke(method, owner, args));
  }

  // public @NotNull AstExpr
  // invoke(@NotNull MethodRef method, @NotNull AstVariable owner, @NotNull ImmutableSeq<AstExpr> args) {
  //   return new AstExpr.Invoke(method, owner, bindExprs(args));
  // }

  public @NotNull IrVariable invoke(@NotNull MethodRef method, @NotNull ImmutableSeq<IrValue> args) {
    return bindExpr(method.returnType(), new IrExpr.Invoke(method, null, args));
  }

  public @NotNull IrVariable refField(@NotNull FieldRef field) {
    return bindExpr(field.returnType(), new IrExpr.RefField(field, null));
  }

  // public @NotNull AstExpr refField(@NotNull FieldRef field, @NotNull AstExpr owner) {
  //   // FIXME: type
  //   return new AstExpr.RefField(field, bindExpr(owner));
  // }

  public @NotNull IrVariable refEnum(@NotNull Enum<?> value) {
    var cd = JavaUtil.fromClass(value.getClass());
    var name = value.name();
    return bindExpr(cd, new IrExpr.RefEnum(cd, name));
  }

  public @NotNull IrVariable checkcast(@NotNull IrVariable obj, @NotNull ClassDesc type) {
    return bindExpr(type, new IrExpr.CheckCast(obj, type));
  }

  public @NotNull IrVariable mkLambda(
    @NotNull ImmutableSeq<IrVariable> captures,
    @NotNull MethodRef method,
    @NotNull BiConsumer<IrArgsProvider.Lambda, IrCodeBuilder> builder
  ) {
    var argc = method.paramTypes().size();
    // [0..captures.size()]th parameters are captures
    // [captures.size()..]th parameters are lambda arguments
    // Note that the [VariablePool] counts from 0,
    // as the arguments does NOT count as [local](AstVariable.Local) variables, but instead a [reference to the argument](AstVariable.Arg).
    var lambdaBodyBuilder = new IrCodeBuilder(owner, FreezableMutableList.create(),
      new VariablePool(), false, false);
    builder.accept(new IrArgsProvider.Lambda(captures.size(), argc), lambdaBodyBuilder);
    var lambdaBody = lambdaBodyBuilder.build();

    return bindExpr(method.owner(), new IrExpr.Lambda(captures, method, lambdaBody));
  }

  public @NotNull IrVariable makeArray(@NotNull ClassDesc elementType, int size, @NotNull ImmutableSeq<IrValue> initializer) {
    return bindExpr(elementType.arrayType(), new IrExpr.Array(elementType, size, initializer));
  }

  // public @NotNull AstExpr getArray(@NotNull AstExpr array, int index) {
  //   return new AstExpr.GetArray(bindExpr(array), index);
  // }

  public @NotNull IrVariable bindExpr(@NotNull IrExpr expr) {
    if (expr instanceof IrExpr.Ref(var ref)) return ref;
    if (expr instanceof IrExpr.Const val) return bindExpr(switch (val) {
      case IrExpr.Bconst _ -> ConstantDescs.CD_boolean;
      case IrExpr.Iconst _ -> ConstantDescs.CD_int;
      case IrExpr.Null(var ty) -> ty;
      case IrExpr.Sconst _ -> ConstantDescs.CD_String;
      case IrExpr.This _ -> owner.parentOrThis();
    }, val);
    // Here we can only trust the callers
    return bindExpr(Constants.CD_Term, expr);
  }

  public @NotNull IrVariable bindExpr(@NotNull ClassDesc desc, @NotNull IrExpr expr) {
    var astVar = acquireVariable();
    stmts.append(new IrStmt.DeclareVariable(desc, astVar, expr));
    return astVar;
  }
}
