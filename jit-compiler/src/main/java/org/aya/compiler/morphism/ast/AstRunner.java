// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.morphism.ast;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableMap;
import org.aya.compiler.AsmOutputCollector;
import org.aya.compiler.LocalVariable;
import org.aya.compiler.morphism.ArgumentProvider;
import org.aya.compiler.morphism.JavaExpr;
import org.aya.compiler.morphism.asm.AsmClassBuilder;
import org.aya.compiler.morphism.asm.AsmCodeBuilder;
import org.aya.compiler.morphism.asm.AsmJavaBuilder;
import org.aya.util.Panic;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;

import java.util.Objects;
import java.util.function.Consumer;

public final class AstRunner<Carrier extends AsmOutputCollector> {
  private final @NotNull AsmJavaBuilder<Carrier> runner;

  // TODO: trying to use MutableArray<LocalVariable>, our VariablePool has a good property
  private @UnknownNullability MutableMap<Integer, LocalVariable> binding;

  public AstRunner(@NotNull AsmJavaBuilder<Carrier> runner) {
    this.runner = runner;
    this.binding = null;
  }

  public Carrier runFree(@NotNull AstDecl.Clazz free) {
    return runner.buildClass(free.metadata(), free.owner(), free.superclass(),
      cb -> runFree(cb, free.members()));
  }

  private void runFree(@NotNull AsmClassBuilder builder, @NotNull ImmutableSeq<AstDecl> frees) {
    frees.forEach(it -> runFree(builder, it));
  }

  private void runFree(@NotNull AsmClassBuilder builder, @NotNull AstDecl free) {
    try (var _ = new SubscopeHandle(MutableMap.create())) {
      switch (free) {
        case AstDecl.Clazz(var metadata, _, var nested, var superclass, var members) -> {
          assert metadata != null && nested != null;
          builder.buildNestedClass(metadata, nested, superclass, cb -> runFree(cb, members));
        }
        case AstDecl.ConstantField constantField ->
          builder.buildConstantField(constantField.signature().returnType(), constantField.signature().name(),
            eb -> {
              throw new UnsupportedOperationException("Not implemented");
            });
        case AstDecl.Method(var sig, var body) -> {
          if (sig.isConstructor()) {
            builder.buildConstructor(sig.paramTypes(),
              (ap, cb) -> interpStmt(ap, cb, body));
          } else {
            builder.buildMethod(sig.returnType(), sig.name(), sig.paramTypes(),
              (ap, cb) -> interpStmt(ap, cb, body));
          }
        }
      }
    }
  }

  private @NotNull ImmutableSeq<JavaExpr> interpExpr(@Nullable ArgumentProvider ap, @NotNull AsmCodeBuilder builder, @NotNull ImmutableSeq<AstExpr> exprs) {
    return exprs.map(it -> runFree(ap, builder, it));
  }

  private ImmutableSeq<LocalVariable> runFree(@Nullable ArgumentProvider ap, @NotNull ImmutableSeq<AstVariable> vars) {
    return vars.map(it -> runFree(ap, it));
  }

  private LocalVariable runFree(@Nullable ArgumentProvider ap, @NotNull AstVariable var) {
    return switch (var) {
      case AstVariable.Local local -> getVar(local.index());
      case AstVariable.Arg arg -> {
        if (ap == null) yield Panic.unreachable();
        yield ap.arg(arg.nth());
      }
    };
  }

  private JavaExpr runFree(@Nullable ArgumentProvider ap, @NotNull AsmCodeBuilder builder, @NotNull AstExpr expr) {
    return switch (expr) {
      case AstExpr.Array(var type, var length, var initializer) ->
        builder.mkArray(type, length, initializer == null ? null : runFree(ap, initializer));
      case AstExpr.CheckCast(var obj, var as) -> builder.checkcast(runFree(ap, obj), as);
      case AstExpr.Iconst(var i) -> builder.iconst(i);
      case AstExpr.Bconst(var b) -> builder.iconst(b);
      case AstExpr.Sconst(var s) -> builder.aconst(s);
      case AstExpr.Null(var ty) -> builder.aconstNull(ty);
      case AstExpr.GetArray(var arr, var idx) -> builder.getArray(runFree(ap, arr), idx);
      case AstExpr.Invoke(var ref, var owner, var args) -> {
        var argsExpr = runFree(ap, args);
        yield owner == null
          ? builder.invoke(ref, argsExpr)
          : builder.invoke(ref, runFree(ap, owner), argsExpr);
      }
      case AstExpr.Lambda(var lamCaptures, var methodRef, var body) -> {
        var captureExprs = runFree(ap, lamCaptures);

        // run captures outside subscope!
        // brand-new scope! the lambda body lives in a difference place to the current scope
        try (var _ = new SubscopeHandle(MutableMap.create())) {
          yield builder.mkLambda(captureExprs, methodRef, (lap, cb) ->
            interpStmt(lap, cb, body));
        }
      }
      case AstExpr.New(var ref, var args) -> builder.mkNew(ref, runFree(ap, args));
      case AstExpr.RefEnum(var enumClass, var enumName) -> builder.refEnum(enumClass, enumName);
      case AstExpr.RefField(var fieldRef, var owner) -> owner != null
        ? builder.refField(fieldRef, runFree(ap, owner))
        : builder.refField(fieldRef);
      case AstExpr.This _ -> builder.thisRef();
      case AstExpr.RefCapture(var idx) -> {
        if (!(ap instanceof ArgumentProvider.Lambda lap)) {
          yield Panic.unreachable();
        }

        yield lap.capture(idx);
      }
    };
  }

  private void interpStmt(@NotNull ArgumentProvider ap, @NotNull AsmCodeBuilder builder, @NotNull ImmutableSeq<AstStmt> free) {
    free.forEach(it -> runFree(ap, builder, it));
  }

  private void runFree(@NotNull ArgumentProvider ap, @NotNull AsmCodeBuilder builder, @NotNull AstStmt free) {
    switch (free) {
      case AstStmt.Break _ -> builder.breakOut();
      case AstStmt.Unreachable _ -> builder.unreachable();
      case AstStmt.Breakable(var inner) -> builder.breakable(cb -> interpStmt(ap, cb, inner));
      case AstStmt.WhileTrue(var inner) -> builder.whileTrue(cb -> interpStmt(ap, cb, inner));
      case AstStmt.Continue _ -> builder.continueLoop();
      case AstStmt.DeclareVariable mkVar -> bindVar(mkVar.theVar().index(), builder.makeVar(mkVar.type(), null));
      case AstStmt.Exec exec -> builder.exec(runFree(ap, builder, exec.expr()));
      case AstStmt.IfThenElse(var cond, var thenBody, var elseBody) -> {
        Consumer<AsmCodeBuilder> thenBlock = cb -> {
          try (var _ = subscoped()) {
            interpStmt(ap, cb, thenBody);
          }
        };
        Consumer<AsmCodeBuilder> elseBlock = elseBody != null
          ? cb -> {
          try (var _ = subscoped()) {
            interpStmt(ap, cb, elseBody);
          }
        } : null;

        switch (cond) {
          case AstStmt.Condition.IsFalse(var isFalse) -> builder.ifNotTrue(runFree(ap, isFalse), thenBlock, elseBlock);
          case AstStmt.Condition.IsTrue(var isTrue) -> builder.ifTrue(runFree(ap, isTrue), thenBlock, elseBlock);
          case AstStmt.Condition.IsInstanceOf(var lhs, var rhs, var as) -> {
            var asTerm = as.get();
            assert asTerm != null;
            builder.ifInstanceOf(runFree(ap, builder, lhs), rhs, (cb, var) -> {
              try (var _ = subscoped()) {
                bindVar(asTerm.index(), var);
                interpStmt(ap, cb, thenBody);      // prevent unnecessary subscoping
              }
            }, elseBlock);
          }
          case AstStmt.Condition.IsIntEqual(var lhs, var rhs) ->
            builder.ifIntEqual(runFree(ap, builder, lhs), rhs, thenBlock, elseBlock);
          case AstStmt.Condition.IsNull(var ref) -> builder.ifNull(runFree(ap, builder, ref), thenBlock, elseBlock);
          case AstStmt.Condition.IsRefEqual(var lhs, var rhs) ->
            builder.ifRefEqual(runFree(ap, builder, lhs), runFree(ap, builder, rhs), thenBlock, elseBlock);
        }
      }
      case AstStmt.Return(var expr) -> builder.returnWith(runFree(ap, builder, expr));
      case AstStmt.SetArray(var arr, var idx, var update) ->
        builder.updateArray(runFree(ap, builder, arr), idx, runFree(ap, builder, update));
      case AstStmt.SetVariable(var var, var update) ->
        builder.updateVar(runFree(ap, var), runFree(ap, builder, update));
      case AstStmt.Super(var params, var args) -> builder.invokeSuperCon(params, interpExpr(ap, builder, args));
      case AstStmt.Switch(var elim, var cases, var branches, var defaultCase) ->
        builder.switchCase(runFree(ap, elim), cases, (cb, kase) -> {
          // slow impl, i am lazy
          int idx = cases.indexOf(kase);
          assert idx != -1;
          var branch = branches.get(idx);
          interpStmt(ap, cb, branch);
        }, cb -> interpStmt(ap, cb, defaultCase));
    }
  }

  private @NotNull LocalVariable getVar(int index) {
    return Objects.requireNonNull(binding.getOrNull(index), "No substitution for local variable: " + index);
  }

  private void bindVar(int index, @NotNull LocalVariable userVar) {
    var exists = binding.put(index, userVar);
    if (exists.isNotEmpty()) Panic.unreachable();
  }

  private class SubscopeHandle implements AutoCloseable {
    private final @UnknownNullability MutableMap<Integer, LocalVariable> oldBinding = binding;
    public SubscopeHandle(@NotNull MutableMap<Integer, LocalVariable> newScope) { binding = newScope; }
    @Override public void close() { binding = oldBinding; }
  }

  private @NotNull SubscopeHandle subscoped() {
    return new SubscopeHandle(MutableMap.from(binding));
  }
}
