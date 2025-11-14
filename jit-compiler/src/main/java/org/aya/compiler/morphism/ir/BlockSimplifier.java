// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.morphism.ir;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import org.aya.compiler.morphism.ir.IrDecl.Clazz;
import org.aya.compiler.morphism.ir.IrDecl.ConstantField;
import org.aya.compiler.morphism.ir.IrDecl.Method;
import org.jetbrains.annotations.NotNull;

/// This class removes unnecessary `break` statements (at the end of breakable blocks)
/// and simplifies `switch` statements by removing some absurd cases, and flattening
/// some `switch` statements into `if-then-else` statements or even removing them
/// if they have only one branch.
public interface BlockSimplifier {
  static @NotNull Clazz optimizeClass(Clazz clazz) {
    return (Clazz) optimize(clazz);
  }

  static @NotNull IrDecl optimize(IrDecl decl) {
    return switch (decl) {
      case Clazz(var metadata, var owner, var nested, var superclass, var members) -> {
        var newMembers = members.map(BlockSimplifier::optimize);
        yield new Clazz(metadata, owner, nested, superclass, newMembers);
      }
      case ConstantField field -> field;
      case Method(var signature, var isStatic, var body) -> new Method(signature, isStatic,
        optimizeBlock(body, false));
      case IrDecl.StaticInitBlock(var block) -> new IrDecl.StaticInitBlock(optimizeBlock(block, false));
    };
  }

  static @NotNull ImmutableSeq<IrStmt> optimizeBlock(ImmutableSeq<IrStmt> block, boolean endOfBreakable) {
    if (!endOfBreakable) return block.flatMap(it -> optimize(it, false));
    if (block.isEmpty()) return block;
    var exceptLast = block.view().dropLast(1).flatMap(it -> optimize(it, false));
    var last = optimize(block.getLast(), true);
    return exceptLast.concat(last).toSeq();
  }

  static @NotNull SeqView<IrStmt> optimize(IrStmt stmt, boolean endOfBreakable) {
    return switch (stmt) {
      case IrStmt.Switch(var elim, var cases, var branch, var defaultCase) -> {
        branch = branch.map(it -> optimizeBlock(it, endOfBreakable));
        defaultCase = optimizeBlock(defaultCase, endOfBreakable);
        if (branch.isEmpty()) yield defaultCase.view();
        if (defaultCase.sizeEquals(1) && defaultCase.getFirst() == IrStmt.SingletonStmt.Unreachable) {
          if (branch.sizeEquals(1)) {
            yield branch.getFirst().view();
          } else if (branch.sizeEquals(2)) {
            yield SeqView.of(new IrStmt.IfThenElse(
              new IrStmt.Condition.IsIntEqual(elim, cases.getFirst()),
              branch.getFirst(),
              branch.getLast()
            ));
          } else if (branch.sizeGreaterThan(1)) {
            yield SeqView.of(new IrStmt.Switch(elim, cases.dropLast(1), branch.dropLast(1), branch.getLast()));
          }
        }
        yield SeqView.of(new IrStmt.Switch(elim, cases, branch, defaultCase));
      }
      case IrStmt.Breakable(var stmts) -> SeqView.of(new IrStmt.Breakable(optimizeBlock(stmts, true)));
      case IrStmt.IfThenElse(var cond, var thenBlock, var elseBlock) -> {
        thenBlock = optimizeBlock(thenBlock, endOfBreakable);
        if (elseBlock != null) {
          elseBlock = optimizeBlock(elseBlock, endOfBreakable);
          if (elseBlock.isEmpty()) elseBlock = null;
        }
        yield SeqView.of(new IrStmt.IfThenElse(cond, thenBlock, elseBlock));
      }
      case IrStmt.SingletonStmt st when st == IrStmt.SingletonStmt.Break && endOfBreakable -> SeqView.empty();
      case IrStmt.WhileTrue(var stmts) -> SeqView.of(new IrStmt.WhileTrue(optimizeBlock(stmts, true)));
      default -> SeqView.of(stmt);
    };
  }
}
