// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.morphism.ast;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import org.aya.compiler.morphism.ast.AstDecl.Clazz;
import org.aya.compiler.morphism.ast.AstDecl.ConstantField;
import org.aya.compiler.morphism.ast.AstDecl.Method;
import org.jetbrains.annotations.NotNull;

/// This class removes unnecessary `break` statements (at the end of breakable blocks)
/// and simplifies `switch` statements by removing some absurd cases, and flattening
/// some `switch` statements into `if-then-else` statements or even removing them
/// if they have only one branch.
public interface BlockSimplifier {
  static @NotNull Clazz optimizeClass(Clazz clazz) {
    return (Clazz) optimize(clazz);
  }

  static @NotNull AstDecl optimize(AstDecl decl) {
    return switch (decl) {
      case Clazz(var metadata, var owner, var nested, var superclass, var members) -> {
        var newMembers = members.map(BlockSimplifier::optimize);
        yield new Clazz(metadata, owner, nested, superclass, newMembers);
      }
      case ConstantField field -> field;
      case Method(var signature, var body) -> new Method(signature, optimizeBlock(body, false));
      case AstDecl.StaticInitBlock(var block) -> new AstDecl.StaticInitBlock(optimizeBlock(block, false));
    };
  }

  static @NotNull ImmutableSeq<AstStmt> optimizeBlock(ImmutableSeq<AstStmt> block, boolean endOfBreakable) {
    if (!endOfBreakable) return block.flatMap(it -> optimize(it, false));
    if (block.isEmpty()) return block;
    var exceptLast = block.view().dropLast(1).flatMap(it -> optimize(it, false));
    var last = optimize(block.getLast(), true);
    return exceptLast.concat(last).toSeq();
  }

  static @NotNull SeqView<AstStmt> optimize(AstStmt stmt, boolean endOfBreakable) {
    return switch (stmt) {
      case AstStmt.Switch(var elim, var cases, var branch, var defaultCase) -> {
        branch = branch.map(it -> optimizeBlock(it, endOfBreakable));
        defaultCase = optimizeBlock(defaultCase, endOfBreakable);
        if (branch.isEmpty()) yield defaultCase.view();
        if (defaultCase.sizeEquals(1) && defaultCase.getFirst() == AstStmt.Unreachable.INSTANCE) {
          if (branch.sizeEquals(1)) {
            yield branch.getFirst().view();
          } else if (branch.sizeEquals(2)) {
            yield SeqView.of(new AstStmt.IfThenElse(
              new AstStmt.Condition.IsIntEqual(elim, cases.getFirst()),
              branch.getFirst(),
              branch.getLast()
            ));
          } else if (branch.sizeGreaterThan(1)) {
            yield SeqView.of(new AstStmt.Switch(elim, cases.dropLast(1), branch.dropLast(1), branch.getLast()));
          }
        }
        yield SeqView.of(new AstStmt.Switch(elim, cases, branch, defaultCase));
      }
      case AstStmt.Breakable(var stmts) -> SeqView.of(new AstStmt.Breakable(optimizeBlock(stmts, true)));
      case AstStmt.IfThenElse(var cond, var thenBlock, var elseBlock) -> {
        thenBlock = optimizeBlock(thenBlock, endOfBreakable);
        if (elseBlock != null) {
          elseBlock = optimizeBlock(elseBlock, endOfBreakable);
          if (elseBlock.isEmpty()) elseBlock = null;
        }
        yield SeqView.of(new AstStmt.IfThenElse(cond, thenBlock, elseBlock));
      }
      case AstStmt.Break _ when endOfBreakable -> SeqView.empty();
      default -> SeqView.of(stmt);
    };
  }
}
