// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.free.morphism.free;

import kala.collection.immutable.ImmutableSeq;
import org.aya.compiler.free.morphism.free.FreeDecl.Clazz;
import org.aya.compiler.free.morphism.free.FreeDecl.ConstantField;
import org.aya.compiler.free.morphism.free.FreeDecl.Method;
import org.jetbrains.annotations.NotNull;

public interface FreeOptimizer {
  static @NotNull Clazz optimizeClass(Clazz clazz) {
    return (Clazz) optimize(clazz);
  }

  static @NotNull FreeDecl optimize(FreeDecl decl) {
    return switch (decl) {
      case Clazz(var metadata, var owner, var nested, var superclass, var members) -> {
        var newMembers = members.map(FreeOptimizer::optimize);
        yield new Clazz(metadata, owner, nested, superclass, newMembers);
      }
      case ConstantField field -> field;
      case Method(var signature, var body) -> new Method(signature, body.flatMap(FreeOptimizer::optimize));
    };
  }

  static @NotNull ImmutableSeq<FreeStmt> optimize(FreeStmt stmt) {
    return switch (stmt) {
      case FreeStmt.Switch(var elim, var cases, var branch, var defaultCase) -> {
        branch = branch.map(it -> it.flatMap(FreeOptimizer::optimize));
        defaultCase = defaultCase.flatMap(FreeOptimizer::optimize);
        if (branch.isEmpty()) yield defaultCase;
        if (defaultCase.sizeEquals(1) && defaultCase.getFirst() == FreeStmt.Unreachable.INSTANCE) {
          if (branch.sizeEquals(1)) {
            yield branch.getFirst();
          } else if (branch.sizeEquals(2)) {
            yield ImmutableSeq.of(new FreeStmt.IfThenElse(
              new FreeStmt.Condition.IsIntEqual(new FreeExpr.RefVariable(elim), cases.getFirst()),
              branch.getFirst(),
              branch.getLast()
            ));
          } else if (branch.sizeGreaterThan(1)) {
            yield ImmutableSeq.of(new FreeStmt.Switch(elim, cases.dropLast(1), branch.dropLast(1), branch.getLast()));
          }
        }
        yield ImmutableSeq.of(new FreeStmt.Switch(elim, cases, branch, defaultCase));
      }
      case FreeStmt.Breakable(var stmts) ->
        ImmutableSeq.of(new FreeStmt.Breakable(stmts.flatMap(FreeOptimizer::optimize)));
      case FreeStmt.IfThenElse(var cond, var thenBlock, var elseBlock) -> {
        var newThenBlock = thenBlock.flatMap(FreeOptimizer::optimize);
        var newElseBlock = elseBlock == null ? null : elseBlock.flatMap(FreeOptimizer::optimize);
        yield ImmutableSeq.of(new FreeStmt.IfThenElse(cond, newThenBlock, newElseBlock));
      }
      default -> ImmutableSeq.of(stmt);
    };
  }
}
