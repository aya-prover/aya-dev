// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.free.morphism.free;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableMap;
import org.aya.compiler.free.*;
import org.aya.compiler.free.data.LocalVariable;
import org.aya.util.error.Panic;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;

import java.util.Objects;
import java.util.function.Consumer;

public final class FreeRunner<Carrier> {
  private final @NotNull FreeJavaBuilder<Carrier> runner;

  // TODO: trying to use MutableArray<LocalVariable>, our VariablePool has a good property
  private @UnknownNullability MutableMap<Integer, LocalVariable> binding;

  public FreeRunner(@NotNull FreeJavaBuilder<Carrier> runner) {
    this.runner = runner;
    this.binding = null;
  }

  public Carrier runFree(@NotNull FreeDecl.Clazz free) {
    return runner.buildClass(free.metadata(), free.owner(), free.superclass(),
      cb -> runFree(cb, free.members()));
  }

  private void runFree(@NotNull FreeClassBuilder builder, @NotNull ImmutableSeq<FreeDecl> frees) {
    frees.forEach(it -> runFree(builder, it));
  }

  private void runFree(@NotNull FreeClassBuilder builder, @NotNull FreeDecl free) {
    try (var _ = new SubscopeHandle(MutableMap.create())) {
      switch (free) {
        case FreeDecl.Clazz(var metadata, _, var nested, var superclass, var members) -> {
          assert metadata != null && nested != null;
          builder.buildNestedClass(metadata, nested, superclass, cb -> runFree(cb, members));
        }
        case FreeDecl.ConstantField constantField ->
          builder.buildConstantField(constantField.signature().returnType(), constantField.signature().name(),
            eb -> runFree(null, eb, constantField.init()));
        case FreeDecl.Method(var sig, var body) -> {
          if (sig.isConstructor()) {
            builder.buildConstructor(sig.paramTypes(),
              (ap, cb) -> runFree(ap, cb, body));
          } else {
            builder.buildMethod(sig.returnType(), sig.name(), sig.paramTypes(),
              (ap, cb) -> runFree(ap, cb, body));
          }
        }
      }
    }
  }

  private @NotNull ImmutableSeq<FreeJavaExpr> runFree(@Nullable ArgumentProvider ap, @NotNull FreeExprBuilder builder, @NotNull ImmutableSeq<FreeExpr> exprs) {
    return exprs.map(it -> runFree(ap, builder, it));
  }

  private LocalVariable runFree(@Nullable ArgumentProvider ap, @NotNull FreeVariable var) {
    return switch (var) {
      case FreeVariable.Local local -> getVar(local.index());
      case FreeVariable.Arg arg -> {
        if (ap == null) yield Panic.unreachable();
        yield ap.arg(arg.nth());
      }
    };
  }

  private FreeJavaExpr runFree(@Nullable ArgumentProvider ap, @NotNull FreeExprBuilder builder, @NotNull FreeExpr expr) {
    return switch (expr) {
      case FreeExpr.RefVariable(var theVar) -> builder.refVar(runFree(ap, theVar));
      case FreeExpr.Array(var type, var length, var initializer) ->
        builder.mkArray(type, length, initializer == null ? null : runFree(ap, builder, initializer));
      case FreeExpr.CheckCast(var obj, var as) -> builder.checkcast(runFree(ap, builder, obj), as);
      case FreeExpr.Iconst(var i) -> builder.iconst(i);
      case FreeExpr.Bconst(var b) -> builder.iconst(b);
      case FreeExpr.Sconst(var s) -> builder.aconst(s);
      case FreeExpr.Null(var ty) -> builder.aconstNull(ty);
      case FreeExpr.GetArray(var arr, var idx) -> builder.getArray(runFree(ap, builder, arr), idx);
      case FreeExpr.Invoke(var ref, var owner, var args) -> {
        var argsExpr = runFree(ap, builder, args);
        yield owner == null
          ? builder.invoke(ref, argsExpr)
          : builder.invoke(ref, runFree(ap, builder, owner), argsExpr);
      }
      case FreeExpr.Lambda(var lamCaptures, var methodRef, var body) -> {
        var captureExprs = runFree(ap, builder, lamCaptures);

        // run captures outside of subscope!
        // brand new scope! the lambda body lives in a difference place to the current scope
        try (var _ = new SubscopeHandle(MutableMap.create())) {
          yield builder.mkLambda(captureExprs, methodRef, (lap, cb) ->
            runFree(lap, cb, body));
        }
      }
      case FreeExpr.New(var ref, var args) -> builder.mkNew(ref, runFree(ap, builder, args));
      case FreeExpr.RefEnum(var enumClass, var enumName) -> builder.refEnum(enumClass, enumName);
      case FreeExpr.RefField(var fieldRef, var owner) -> owner != null
        ? builder.refField(fieldRef, runFree(ap, builder, owner))
        : builder.refField(fieldRef);
      case FreeExpr.This _ -> builder.thisRef();
      case FreeExpr.RefCapture(var idx) -> {
        if (!(ap instanceof ArgumentProvider.Lambda lap)) {
          yield Panic.unreachable();
        }

        yield lap.capture(idx);
      }
    };
  }

  private void runFree(@NotNull ArgumentProvider ap, @NotNull FreeCodeBuilder builder, @NotNull ImmutableSeq<FreeStmt> free) {
    free.forEach(it -> runFree(ap, builder, it));
  }

  private void runFree(@NotNull ArgumentProvider ap, @NotNull FreeCodeBuilder builder, @NotNull FreeStmt free) {
    switch (free) {
      case FreeStmt.Break _ -> builder.breakOut();
      case FreeStmt.Breakable(var inner) -> builder.breakable(cb -> runFree(ap, cb, inner));
      case FreeStmt.DeclareVariable mkVar -> bindVar(mkVar.theVar().index(), builder.makeVar(mkVar.type(), null));
      case FreeStmt.Exec exec -> builder.exec(runFree(ap, builder, exec.expr()));
      case FreeStmt.IfThenElse(var cond, var thenBody, var elseBody) -> {
        Consumer<FreeCodeBuilder> thenBlock = cb -> {
          try (var _ = subscoped()) {
            runFree(ap, cb, thenBody);
          }
        };
        Consumer<FreeCodeBuilder> elseBlock = elseBody != null
          ? cb -> {
          try (var _ = subscoped()) {
            runFree(ap, cb, elseBody);
          }
        } : null;

        switch (cond) {
          case FreeStmt.Condition.IsFalse(var isFalse) -> builder.ifNotTrue(runFree(ap, isFalse), thenBlock, elseBlock);
          case FreeStmt.Condition.IsTrue(var isTrue) -> builder.ifTrue(runFree(ap, isTrue), thenBlock, elseBlock);
          case FreeStmt.Condition.IsInstanceOf(var lhs, var rhs, var as) -> {
            var asTerm = as.get();
            assert asTerm != null;
            builder.ifInstanceOf(runFree(ap, builder, lhs), rhs, (cb, var) -> {
              try (var _ = subscoped()) {
                bindVar(asTerm.index(), var);
                runFree(ap, cb, thenBody);      // prevent unnecessary subscoping
              }
            }, elseBlock);
          }
          case FreeStmt.Condition.IsIntEqual(var lhs, var rhs) ->
            builder.ifIntEqual(runFree(ap, builder, lhs), rhs, thenBlock, elseBlock);
          case FreeStmt.Condition.IsNull(var ref) -> builder.ifNull(runFree(ap, builder, ref), thenBlock, elseBlock);
          case FreeStmt.Condition.IsRefEqual(var lhs, var rhs) ->
            builder.ifRefEqual(runFree(ap, builder, lhs), runFree(ap, builder, rhs), thenBlock, elseBlock);
        }
      }
      case FreeStmt.Return(var expr) -> builder.returnWith(runFree(ap, builder, expr));
      case FreeStmt.SetArray(var arr, var idx, var update) ->
        builder.updateArray(runFree(ap, builder, arr), idx, runFree(ap, builder, update));
      case FreeStmt.SetVariable(var var, var update) ->
        builder.updateVar(runFree(ap, var), runFree(ap, builder, update));
      case FreeStmt.Super(var params, var args) -> builder.invokeSuperCon(params, runFree(ap, builder, args));
      case FreeStmt.Switch(var elim, var cases, var branches, var defaultCase) ->
        builder.switchCase(runFree(ap, elim), cases, (cb, kase) -> {
          // slow impl, i am lazy
          int idx = cases.indexOf(kase);
          assert idx != -1;
          var branch = branches.get(idx);
          runFree(ap, cb, branch);
        }, cb -> runFree(ap, cb, defaultCase));
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

    public SubscopeHandle(
      @NotNull MutableMap<Integer, LocalVariable> newScope
    ) {
      binding = newScope;
    }

    @Override
    public void close() {
      binding = oldBinding;
    }
  }

  private @NotNull SubscopeHandle subscoped() {
    // dont change captures!!
    return new SubscopeHandle(MutableMap.from(binding));
  }

  // private static class ArgumentProvider implements ArgumentProvider {
  //   public final @NotNull FreeRunner<?> runner;
  //   public final @NotNull ArgumentProvider userAp;
  //
  //   private ArgumentProvider(@NotNull FreeRunner<?> runner, @NotNull ArgumentProvider userAp) {
  //     this.runner = runner;
  //     this.userAp = userAp;
  //   }
  //
  //   @Override
  //   public @NotNull LocalVariable arg(int nth) {
  //     var exists = runner.binding.getOrNull(nth);
  //     var userVar = userAp.arg(nth);
  //     if (exists == null) runner.bindVar(nth, userVar);
  //     assert exists == null || exists.equals(userVar);
  //     return userVar;
  //   }
  //
  //   public final static class Lambda extends ArgumentProvider implements ArgumentProvider.Lambda {
  //     private Lambda(@NotNull FreeRunner<?> runner, @NotNull ArgumentProvider.Lambda userAp) {
  //       super(runner, userAp);
  //     }
  //
  //     @Override
  //     public @NotNull FreeJavaExpr capture(int nth) {
  //       var exists = runner.captures.getOrNull(nth);
  //       var userCpature = ((ArgumentProvider.Lambda) userAp).capture(nth);
  //       if (exists == null) runner.bindCapture(nth, userCpature);
  //       assert exists == null || exists.equals(userCpature);
  //       return userCpature;
  //     }
  //   }
  // }
}
