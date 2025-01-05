// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.free.morphism.free;

import kala.collection.SeqView;
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
      case Method(var signature, var body) -> new Method(signature, optimizeBlock(body, false));
    };
  }

  static @NotNull ImmutableSeq<FreeStmt> optimizeBlock(ImmutableSeq<FreeStmt> block, boolean endOfBreakable) {
    if (!endOfBreakable) return block.flatMap(it -> optimize(it, false));
    var exceptLast = block.view().dropLast(1).flatMap(it -> optimize(it, false));
    var last = optimize(block.getLast(), true);
    return exceptLast.concat(last).toImmutableSeq();
  }

  static @NotNull SeqView<FreeStmt> optimize(FreeStmt stmt, boolean endOfBreakable) {
    return switch (stmt) {
      case FreeStmt.Switch(var elim, var cases, var branch, var defaultCase) -> {
        branch = branch.map(it -> optimizeBlock(it, endOfBreakable));
        defaultCase = optimizeBlock(defaultCase, endOfBreakable);
        if (branch.isEmpty()) yield defaultCase.view();
        if (defaultCase.sizeEquals(1) && defaultCase.getFirst() == FreeStmt.Unreachable.INSTANCE) {
          if (branch.sizeEquals(1)) {
            yield branch.getFirst().view();
          } else if (branch.sizeEquals(2)) {
            yield SeqView.of(new FreeStmt.IfThenElse(
              new FreeStmt.Condition.IsIntEqual(new FreeExpr.RefVariable(elim), cases.getFirst()),
              branch.getFirst(),
              branch.getLast()
            ));
          } else if (branch.sizeGreaterThan(1)) {
            yield SeqView.of(new FreeStmt.Switch(elim, cases.dropLast(1), branch.dropLast(1), branch.getLast()));
          }
        }
        yield SeqView.of(new FreeStmt.Switch(elim, cases, branch, defaultCase));
      }
      case FreeStmt.Breakable(var stmts) -> SeqView.of(new FreeStmt.Breakable(optimizeBlock(stmts, true)));
      case FreeStmt.IfThenElse(var cond, var thenBlock, var elseBlock) -> {
        thenBlock = optimizeBlock(thenBlock, endOfBreakable);
        if (elseBlock != null) {
          elseBlock = optimizeBlock(elseBlock, endOfBreakable);
          if (elseBlock.isEmpty()) elseBlock = null;
        }
        yield SeqView.of(new FreeStmt.IfThenElse(cond, thenBlock, elseBlock));
      }
      case FreeStmt.Break _ when endOfBreakable -> SeqView.empty();
      default -> SeqView.of(stmt);
    };
  }
}
