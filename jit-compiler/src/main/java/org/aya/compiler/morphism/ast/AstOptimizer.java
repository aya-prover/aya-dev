// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.morphism.ast;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import org.aya.compiler.ir.IRStmt;
import org.aya.compiler.morphism.ast.AstDecl.Clazz;
import org.aya.compiler.morphism.ast.AstDecl.ConstantField;
import org.aya.compiler.morphism.ast.AstDecl.Method;
import org.jetbrains.annotations.NotNull;

public interface AstOptimizer {
  static @NotNull Clazz optimizeClass(Clazz clazz) {
    return (Clazz) optimize(clazz);
  }

  static @NotNull AstDecl optimize(AstDecl decl) {
    return switch (decl) {
      case Clazz(var metadata, var owner, var nested, var superclass, var members) -> {
        var newMembers = members.map(AstOptimizer::optimize);
        yield new Clazz(metadata, owner, nested, superclass, newMembers);
      }
      case ConstantField field -> field;
      case Method(var signature, var body) -> new Method(signature, optimizeBlock(body, false));
    };
  }

  static @NotNull ImmutableSeq<IRStmt> optimizeBlock(ImmutableSeq<IRStmt> block, boolean endOfBreakable) {
    if (!endOfBreakable) return block.flatMap(it -> optimize(it, false));
    if (block.isEmpty()) return block;
    var exceptLast = block.view().dropLast(1).flatMap(it -> optimize(it, false));
    var last = optimize(block.getLast(), true);
    return exceptLast.concat(last).toSeq();
  }

  static @NotNull SeqView<IRStmt> optimize(IRStmt stmt, boolean endOfBreakable) {
    return switch (stmt) {
      case IRStmt.Switch(var elim, var cases, var branch, var defaultCase) -> {
        branch = branch.map(it -> optimizeBlock(it, endOfBreakable));
        defaultCase = optimizeBlock(defaultCase, endOfBreakable);
        if (branch.isEmpty()) yield defaultCase.view();
        if (defaultCase.sizeEquals(1) && defaultCase.getFirst() == IRStmt.Unreachable.INSTANCE) {
          if (branch.sizeEquals(1)) {
            yield branch.getFirst().view();
          } else if (branch.sizeEquals(2)) {
            yield SeqView.of(new IRStmt.IfThenElse(
              new IRStmt.Condition.IsIntEqual(new AstExpr.RefVariable(elim), cases.getFirst()),
              branch.getFirst(),
              branch.getLast()
            ));
          } else if (branch.sizeGreaterThan(1)) {
            yield SeqView.of(new IRStmt.Switch(elim, cases.dropLast(1), branch.dropLast(1), branch.getLast()));
          }
        }
        yield SeqView.of(new IRStmt.Switch(elim, cases, branch, defaultCase));
      }
      case IRStmt.Breakable(var stmts) -> SeqView.of(new IRStmt.Breakable(optimizeBlock(stmts, true)));
      case IRStmt.IfThenElse(var cond, var thenBlock, var elseBlock) -> {
        thenBlock = optimizeBlock(thenBlock, endOfBreakable);
        if (elseBlock != null) {
          elseBlock = optimizeBlock(elseBlock, endOfBreakable);
          if (elseBlock.isEmpty()) elseBlock = null;
        }
        yield SeqView.of(new IRStmt.IfThenElse(cond, thenBlock, elseBlock));
      }
      case IRStmt.Break _ when endOfBreakable -> SeqView.empty();
      default -> SeqView.of(stmt);
    };
  }
}
