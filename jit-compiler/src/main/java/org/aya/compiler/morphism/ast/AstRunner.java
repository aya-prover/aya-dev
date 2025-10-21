// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.morphism.ast;

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

public final class AstRunner<Carrier extends AsmOutputCollector> {
  private final @NotNull AsmJavaBuilder<Carrier> runner;

  // TODO: trying to use MutableArray<AsmVariable>, our VariablePool has a good property
  private @UnknownNullability MutableMap<Integer, AsmVariable> binding;

  public AstRunner(@NotNull AsmJavaBuilder<Carrier> runner) {
    this.runner = runner;
    this.binding = null;
  }

  public Carrier interpClass(@NotNull AstDecl.Clazz free, @NotNull ClassHierarchyResolver hierarchyResolver) {
    return runner.buildClass(free.metadata(), free.owner(), free.superclass(),
      hierarchyResolver, cb -> interpDecls(cb, free.members()));
  }

  private void interpDecls(@NotNull AsmClassBuilder builder, @NotNull ImmutableSeq<AstDecl> frees) {
    frees.forEach(it -> interpDecl(builder, it));
  }

  private void interpDecl(@NotNull AsmClassBuilder builder, @NotNull AstDecl free) {
    try (var _ = new SubscopeHandle(MutableMap.create())) {
      switch (free) {
        case AstDecl.Clazz(var metadata, _, var nested, var superclass, var members) -> {
          assert metadata != null && nested != null;
          builder.buildNestedClass(metadata, nested, superclass, cb -> interpDecls(cb, members));
        }
        case AstDecl.ConstantField constantField ->
          builder.buildStaticField(constantField.signature().returnType(), constantField.signature().name());
        case AstDecl.Method(var sig, var body) -> {
          if (sig.isConstructor()) {
            builder.buildConstructor(sig.paramTypes(),
              (ap, cb) -> interpStmts(ap, cb, body));
          } else {
            builder.buildMethod(sig.returnType(), sig.name(), sig.paramTypes(),
              (ap, cb) -> interpStmts(ap, cb, body));
          }
        }
        case AstDecl.StaticInitBlock(var block) -> builder.buildStaticInitBlock(cb ->
          interpStmts(AsmArgsProvider.EMPTY, cb, block));
      }
    }
  }

  private ImmutableSeq<AsmVariable> interpVars(@Nullable AsmArgsProvider ap, @NotNull ImmutableSeq<AstVariable> vars) {
    return vars.map(it -> interpVar(ap, it));
  }

  private AsmVariable interpVar(@Nullable AsmArgsProvider ap, @NotNull AstVariable var) {
    return switch (var) {
      case AstVariable.Local local -> getVar(local.index());
      case AstVariable.Arg arg -> {
        if (ap == null) yield Panic.unreachable();
        yield switch (ap) {
          case AsmArgsProvider.FnParam aap -> aap.arg(arg.nth());
          case AsmArgsProvider.FnParam.Lambda lap -> lap.arg(arg.nth());
          default -> Panic.unreachable();
        };
      }
      case AstVariable.Capture(var nth) -> {
        if (!(ap instanceof AsmArgsProvider.FnParam.Lambda lap)) yield Panic.unreachable();
        yield lap.capture(nth);
      }
    };
  }

  private AsmExpr interpExpr(@Nullable AsmArgsProvider ap, @NotNull AsmCodeBuilder builder, @NotNull AstExpr expr) {
    return switch (expr) {
      case AstExpr.Ref(var ref) -> AsmExpr.withType(Constants.CD_Term,
        builder0 -> builder0.loadVar(interpVar(ap, ref)));
      case AstExpr.Array(var type, var length, var initializer) ->
        builder.mkArray(type, length, initializer == null ? null : interpVars(ap, initializer));
      case AstExpr.CheckCast(var obj, var as) -> builder.checkcast(interpVar(ap, obj), as);
      case AstExpr.Iconst(var i) -> builder.iconst(i);
      case AstExpr.Bconst(var b) -> builder.iconst(b);
      case AstExpr.Sconst(var s) -> builder.aconst(s);
      case AstExpr.Null(var ty) -> builder.aconstNull(ty);
      case AstExpr.GetArray(var arr, var idx) -> builder.getArray(interpVar(ap, arr), idx);
      case AstExpr.Invoke(var ref, var owner, var args) -> {
        var argsExpr = interpVars(ap, args);
        yield owner == null
          ? builder.invoke(ref, argsExpr)
          : builder.invoke(ref, interpVar(ap, owner), argsExpr);
      }
      case AstExpr.Lambda(var lamCaptures, var methodRef, var body) -> {
        var captureExprs = interpVars(ap, lamCaptures);

        // run captures outside subscope!
        // brand-new scope! the lambda body lives in a difference place to the current scope
        try (var _ = new SubscopeHandle(MutableMap.create())) {
          yield builder.mkLambda(captureExprs, methodRef, (lap, cb) ->
            interpStmts(lap, cb, body));
        }
      }
      case AstExpr.New(var ref, var args) -> builder.mkNew(ref, interpVars(ap, args));
      case AstExpr.RefEnum(var enumClass, var enumName) -> builder.refEnum(enumClass, enumName);
      case AstExpr.RefField(var fieldRef, var owner) -> owner != null
        ? builder.refField(fieldRef, interpVar(ap, owner))
        : builder.refField(fieldRef);
      case AstExpr.This _ -> builder.thisRef().ref();
    };
  }

  private void interpStmts(@NotNull AsmArgsProvider ap, @NotNull AsmCodeBuilder builder, @NotNull ImmutableSeq<AstStmt> free) {
    free.forEach(it -> interpStmt(ap, builder, it));
  }

  private void interpStmt(@NotNull AsmArgsProvider ap, @NotNull AsmCodeBuilder builder, @NotNull AstStmt free) {
    switch (free) {
      case AstStmt.SingletonStmt stmt -> {
        switch (stmt) {
          case Break -> builder.breakOut();
          case Continue -> builder.continueLoop();
          case Unreachable -> builder.unreachable();
        }
      }
      case AstStmt.Breakable(var inner) -> {
        try (var _ = subscoped()) {
          builder.breakable(cb -> interpStmts(ap, cb, inner));
        }
      }
      case AstStmt.WhileTrue(var inner) -> {
        try (var _ = subscoped()) {
          builder.whileTrue(cb -> interpStmts(ap, cb, inner));
        }
      }
      case AstStmt.DeclareVariable(var type, var theVar) -> bindVar(theVar.index(), builder.makeVar(type, null));
      case AstStmt.Exec(var exec) -> builder.exec(interpExpr(ap, builder, exec));
      case AstStmt.IfThenElse(var cond, var thenBody, var elseBody) -> {
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
          case AstStmt.Condition.IsFalse(var isFalse) ->
            builder.ifNotTrue(interpVar(ap, isFalse), thenBlock, elseBlock);
          case AstStmt.Condition.IsTrue(var isTrue) -> builder.ifTrue(interpVar(ap, isTrue), thenBlock, elseBlock);
          case AstStmt.Condition.IsInstanceOf(var lhs, var rhs, var as) -> {
            var asTerm = as.get();
            assert asTerm != null;
            builder.ifInstanceOf(interpVar(ap, lhs), rhs, (cb, var) -> {
              try (var _ = subscoped()) {
                bindVar(asTerm.index(), var);
                interpStmts(ap, cb, thenBody);      // prevent unnecessary subscoping
              }
            }, elseBlock);
          }
          case AstStmt.Condition.IsIntEqual(var lhs, var rhs) ->
            builder.ifIntEqual(interpVar(ap, lhs), rhs, thenBlock, elseBlock);
          case AstStmt.Condition.IsNull(var ref) -> builder.ifNull(interpVar(ap, ref), thenBlock, elseBlock);
          case AstStmt.Condition.IsRefEqual(var lhs, var rhs) ->
            builder.ifRefEqual(interpVar(ap, lhs), interpVar(ap, rhs), thenBlock, elseBlock);
        }
      }
      case AstStmt.Return(var expr) -> builder.returnWith(interpVar(ap, expr));
      case AstStmt.SetArray(var arr, var idx, var update) ->
        builder.updateArray(interpVar(ap, arr), idx, interpVar(ap, update));
      case AstStmt.SetVariable(var var, var update) ->
        builder.updateVar(interpVar(ap, var), interpExpr(ap, builder, update));
      case AstStmt.Super(var params, var args) -> builder.invokeSuperCon(params, interpVars(ap, args));
      case AstStmt.Switch(var elim, var cases, var branches, var defaultCase) ->
        builder.switchCase(interpVar(ap, elim), cases, (cb, kase) -> {
          // slow impl, i am lazy
          int idx = cases.indexOf(kase);
          assert idx != -1;
          var branch = branches.get(idx);
          try (var _ = subscoped()) {
            interpStmts(ap, cb, branch);
          }
        }, cb -> { try (var _ = subscoped()) { interpStmts(ap, cb, defaultCase); } });
      case AstStmt.SetStaticField(var fieldRef, var update) -> builder.setStaticField(fieldRef, interpVar(ap, update));
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
