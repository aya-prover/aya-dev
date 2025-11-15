// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.morphism.ir;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableMap;
import org.aya.compiler.AsmOutputCollector;
import org.aya.compiler.morphism.Constants;
import org.aya.compiler.morphism.asm.*;
import org.aya.util.Panic;
import org.glavo.classfile.ClassHierarchyResolver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;

import java.util.Objects;
import java.util.function.Consumer;

public final class IrCompiler<Carrier extends AsmOutputCollector> {
  private final @NotNull AsmJavaBuilder<Carrier> runner;

  // TODO: trying to use MutableArray<AsmVariable>, our VariablePool has a good property
  private @UnknownNullability MutableMap<Integer, AsmVariable> binding;

  public IrCompiler(@NotNull AsmJavaBuilder<Carrier> runner) {
    this.runner = runner;
    this.binding = null;
  }

  public Carrier interpClass(@NotNull IrDecl.Clazz free, @NotNull ClassHierarchyResolver hierarchyResolver) {
    return runner.buildClass(free.metadata(), free.owner(), free.superclass(),
      hierarchyResolver, cb -> interpDecls(cb, free.members()));
  }

  private void interpDecls(@NotNull AsmClassBuilder builder, @NotNull ImmutableSeq<IrDecl> frees) {
    frees.forEach(it -> interpDecl(builder, it));
  }

  private void interpDecl(@NotNull AsmClassBuilder builder, @NotNull IrDecl free) {
    try (var _ = new SubscopeHandle(MutableMap.create())) {
      switch (free) {
        case IrDecl.Clazz(var metadata, _, var nested, var superclass, var members) -> {
          assert metadata != null && nested != null;
          builder.buildNestedClass(metadata, nested, superclass, cb -> interpDecls(cb, members));
        }
        case IrDecl.ConstantField constantField ->
          builder.buildStaticField(constantField.signature().returnType(), constantField.signature().name());
        case IrDecl.Method(var sig, var isStatic, var body) when isStatic ->
          builder.buildStaticMethod(sig.returnType(), sig.name(), sig.paramTypes(),
            (ap, cb) -> interpStmts(ap, cb, body));
        case IrDecl.Method(var sig, _, var body) -> {
          if (sig.isConstructor()) {
            builder.buildConstructor(sig.paramTypes(),
              (ap, cb) -> interpStmts(ap, cb, body));
          } else {
            builder.buildMethod(sig.returnType(), sig.name(), sig.paramTypes(),
              (ap, cb) -> interpStmts(ap, cb, body));
          }
        }
        case IrDecl.StaticInitBlock(var block) -> builder.buildStaticInitBlock(cb ->
          interpStmts(ArgsProvider.EMPTY, cb, block));
      }
    }
  }

  private ImmutableSeq<AsmValue> interpVars(
    @Nullable ArgsProvider ap,
    @NotNull AsmCodeBuilder builder,
    @NotNull ImmutableSeq<? extends IrValue> vars
  ) {
    return vars.map(it -> interpVar(ap, builder, it));
  }

  private ImmutableSeq<AsmVariable> interpVars(
    @Nullable ArgsProvider ap,
    @NotNull ImmutableSeq<IrVariable> vars
  ) {
    return vars.map(it -> interpVar(ap, it));
  }

  private AsmVariable interpVar(@Nullable ArgsProvider ap, @NotNull IrVariable var) {
    return switch (var) {
      case IrVariable.Local local -> getVar(local.index());
      case IrVariable.Arg arg -> {
        if (ap == null) yield Panic.unreachable();
        yield switch (ap) {
          case ArgsProvider.FnParam aap -> aap.arg(arg.nth());
          case ArgsProvider.FnParam.Lambda lap -> lap.arg(arg.nth());
          default -> Panic.unreachable();
        };
      }
      case IrVariable.Capture(var nth) -> {
        if (!(ap instanceof ArgsProvider.FnParam.Lambda lap))
          yield Panic.unreachable();
        yield lap.capture(nth);
      }
    };
  }

  private AsmValue interpVar(@Nullable ArgsProvider ap, @NotNull AsmCodeBuilder builder, @NotNull IrValue var) {
    return switch (var) {
      case IrVariable variable -> new AsmValue.AsmValuriable(interpVar(ap, variable));
      case IrExpr.Const val -> new AsmValue.AsmExprValue(interpExpr(ap, builder, val));
    };
  }

  private AsmExpr interpExpr(@Nullable ArgsProvider ap, @NotNull AsmCodeBuilder builder, @NotNull IrExpr expr) {
    return switch (expr) {
      case IrExpr.Ref(var ref) -> AsmExpr.withType(Constants.CD_Term,
        builder0 -> interpVar(ap, builder0, ref).accept(builder0));
      case IrExpr.Array(var type, var length, var initializer) ->
        builder.mkArray(type, length, initializer == null ? null : interpVars(ap, builder, initializer));
      case IrExpr.CheckCast(var obj, var as) -> builder.checkcast(interpVar(ap, builder, obj), as);
      case IrExpr.Iconst(var i) -> builder.iconst(i);
      case IrExpr.Bconst(var b) -> builder.iconst(b);
      case IrExpr.Sconst(var s) -> builder.aconst(s);
      case IrExpr.Null(var ty) -> builder.aconstNull(ty);
      case IrExpr.GetArray(var arr, var idx) -> builder.getArray(interpVar(ap, arr), idx);
      case IrExpr.Invoke(var ref, var owner, var args) -> {
        var argsExpr = interpVars(ap, builder, args);
        yield owner == null
          ? builder.invoke(ref, argsExpr)
          : builder.invoke(ref, interpVar(ap, builder, owner), argsExpr);
      }
      case IrExpr.Lambda(var lamCaptures, var methodRef, var body) -> {
        var captureExprs = interpVars(ap, lamCaptures);

        // run captures outside subscope!
        // brand-new scope! the lambda body lives in a difference place to the current scope
        try (var _ = new SubscopeHandle(MutableMap.create())) {
          yield builder.mkLambda(captureExprs, methodRef, (lap, cb) ->
            interpStmts(lap, cb, body));
        }
      }
      case IrExpr.New(var ref, var args) -> builder.mkNew(ref, interpVars(ap, builder, args));
      case IrExpr.RefEnum(var enumClass, var enumName) -> builder.refEnum(enumClass, enumName);
      case IrExpr.RefField(var fieldRef, var owner) -> owner != null
        ? builder.refField(fieldRef, interpVar(ap, builder, owner))
        : builder.refField(fieldRef);
      case IrExpr.This _ -> builder.thisRef().ref();
    };
  }

  private void interpStmts(@NotNull ArgsProvider ap, @NotNull AsmCodeBuilder builder, @NotNull ImmutableSeq<IrStmt> free) {
    free.forEach(it -> interpStmt(ap, builder, it));
  }

  private void interpStmt(@NotNull ArgsProvider ap, @NotNull AsmCodeBuilder builder, @NotNull IrStmt free) {
    switch (free) {
      case IrStmt.SingletonStmt stmt -> {
        switch (stmt) {
          case Break -> builder.breakOut();
          case Continue -> builder.continueLoop();
          case Unreachable -> builder.unreachable();
        }
      }
      case IrStmt.Breakable(var inner) -> {
        try (var _ = subscoped()) {
          builder.breakable(cb -> interpStmts(ap, cb, inner));
        }
      }
      case IrStmt.WhileTrue(var inner) -> {
        try (var _ = subscoped()) {
          builder.whileTrue(cb -> interpStmts(ap, cb, inner));
        }
      }
      case IrStmt.DeclareVariable(var type, var theVar, var initializer) -> {
        bindVar(theVar.index(), builder.makeVar(type, null));
        if (initializer != null) {
          builder.updateVar(getVar(theVar.index()), interpExpr(ap, builder, initializer));
        }
      }
      case IrStmt.Exec(var exec) -> builder.exec(interpExpr(ap, builder, exec));
      case IrStmt.IfThenElse(var cond, var thenBody, var elseBody) -> {
        Consumer<AsmCodeBuilder> thenBlock = cb -> {
          try (var _ = subscoped()) {
            interpStmts(ap, cb, thenBody);
          }
        };
        Consumer<AsmCodeBuilder> elseBlock = elseBody != null
          ? cb -> {
          try (var _ = subscoped()) {
            interpStmts(ap, cb, elseBody);
          }
        } : null;

        switch (cond) {
          case IrStmt.Condition.IsFalse(var isFalse) ->
            builder.ifNotTrue(interpVar(ap, builder, isFalse), thenBlock, elseBlock);
          case IrStmt.Condition.IsTrue(var isTrue) ->
            builder.ifTrue(interpVar(ap, builder, isTrue), thenBlock, elseBlock);
          case IrStmt.Condition.IsInstanceOf(var lhs, var rhs, var as) -> {
            var asTerm = as.get();
            assert asTerm != null;
            builder.ifInstanceOf(interpVar(ap, builder, lhs), rhs, (cb, var) -> {
              try (var _ = subscoped()) {
                bindVar(asTerm.index(), var);
                interpStmts(ap, cb, thenBody); // prevent unnecessary subscoping
              }
            }, elseBlock);
          }
          case IrStmt.Condition.IsIntEqual(var lhs, var rhs) ->
            builder.ifIntEqual(interpVar(ap, builder, lhs), rhs, thenBlock, elseBlock);
          case IrStmt.Condition.IsNull(var ref) -> builder.ifNull(interpVar(ap, ref), thenBlock, elseBlock);
          case IrStmt.Condition.IsRefEqual(var lhs, var rhs) ->
            builder.ifRefEqual(interpVar(ap, lhs), interpVar(ap, rhs), thenBlock, elseBlock);
        }
      }
      case IrStmt.Return(var expr) -> builder.returnWith(interpVar(ap, builder, expr));
      case IrStmt.SetArray(var arr, var idx, var update) ->
        builder.updateArray(interpVar(ap, arr), idx, interpVar(ap, builder, update));
      case IrStmt.SetVariable(var var, var update) ->
        builder.updateVar(interpVar(ap, var), interpExpr(ap, builder, update));
      case IrStmt.Super(var params, var args) -> builder.invokeSuperCon(params, interpVars(ap, builder, args));
      case IrStmt.Switch(var elim, var cases, var branches, var defaultCase) ->
        builder.switchCase(interpVar(ap, elim), cases, (cb, kase) -> {
          // slow impl, i am lazy
          int idx = cases.indexOf(kase);
          assert idx != -1;
          var branch = branches.get(idx);
          try (var _ = subscoped()) {
            interpStmts(ap, cb, branch);
          }
        }, cb -> { try (var _ = subscoped()) { interpStmts(ap, cb, defaultCase); } });
      case IrStmt.SetStaticField(var fieldRef, var update) ->
        builder.setStaticField(fieldRef, interpVar(ap, builder, update));
    }
  }

  private @NotNull AsmVariable getVar(int index) {
    return Objects.requireNonNull(binding.getOrNull(index), "No substitution for local variable: " + index);
  }

  private void bindVar(int index, @NotNull AsmVariable userVar) {
    var exists = binding.put(index, userVar);
    if (exists.isNotEmpty()) Panic.unreachable();
  }

  private class SubscopeHandle implements AutoCloseable {
    private final @UnknownNullability MutableMap<Integer, AsmVariable> oldBinding = binding;
    public SubscopeHandle(@NotNull MutableMap<Integer, AsmVariable> newScope) { binding = newScope; }
    @Override public void close() { binding = oldBinding; }
  }

  private @NotNull SubscopeHandle subscoped() {
    return new SubscopeHandle(MutableMap.from(binding));
  }
}
