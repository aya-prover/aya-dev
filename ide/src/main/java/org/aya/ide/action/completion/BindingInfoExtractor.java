// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.ide.action.completion;

import kala.collection.immutable.ImmutableMap;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableMap;
import org.aya.intellij.GenericNode;
import org.aya.parser.AssociatedNode;
import org.aya.syntax.concrete.Expr;
import org.aya.syntax.concrete.Pattern;
import org.aya.syntax.concrete.stmt.Stmt;
import org.aya.syntax.concrete.stmt.StmtVisitor;
import org.aya.syntax.core.term.Term;
import org.aya.util.position.SourcePos;
import org.jetbrains.annotations.NotNull;

/// Extract [BindingInfo] from [kala.value.MutableValue] and its owner, kinda hacking.
/// It is possible that some [kala.value.MutableValue] is NOT [AssociatedNode], which owner is:
/// * no corresponding binding, such as `Nat -> Nat` (desugared `(_ : Nat) -> Nat`)
/// * comes from desugar
public final class BindingInfoExtractor implements StmtVisitor {
  private final @NotNull MutableMap<GenericNode<?>, BindingInfo> map = MutableMap.create();

  @Override
  public void visitParamDecl(Expr.@NotNull Param param) {
    if (param.theCoreType() instanceof AssociatedNode<Term>(var delegate, var node)) {
      map.putIfAbsent(node, new BindingInfo(param.ref(), param.type(), delegate));
    }

    StmtVisitor.super.visitParamDecl(param);
  }

  @Override
  public void visitLetBind(Expr.@NotNull LetBind bind) {
    if (bind.theCoreType() instanceof AssociatedNode<Term>(var delegate, var node)) {
      map.putIfAbsent(node, new BindingInfo(bind.ref(), bind.result().data(), delegate));
    }

    StmtVisitor.super.visitLetBind(bind);
  }

  @Override
  public void visitPattern(@NotNull SourcePos pos, @NotNull Pattern pat) {
    switch (pat) {
      case Pattern.Bind bind when bind.theCoreType() instanceof AssociatedNode<Term>(var delegate, var node) ->
        map.putIfAbsent(node, new BindingInfo(bind.bind(), null, delegate));
      case Pattern.As as when as.theCoreType() instanceof AssociatedNode<Term>(var delegate, var node) ->
        map.putIfAbsent(node, new BindingInfo(as.as(), null, delegate));
      default -> { }
    }

    StmtVisitor.super.visitPattern(pos, pat);
  }

  public @NotNull BindingInfoExtractor accept(@NotNull ImmutableSeq<Stmt> program) {
    program.forEach(this);
    return this;
  }

  public @NotNull ImmutableMap<GenericNode<?>, BindingInfo> extracted() {
    return ImmutableMap.from(map);
  }

  // TODO: let bind / do bind
}
