// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.ide.action;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import kala.collection.mutable.MutableMap;
import org.aya.ide.syntax.SyntaxNodeAction;
import org.aya.ide.util.XY;
import org.aya.syntax.concrete.Expr;
import org.aya.syntax.concrete.Pattern;
import org.aya.syntax.concrete.stmt.Command;
import org.aya.syntax.concrete.stmt.ModuleName;
import org.aya.syntax.concrete.stmt.Stmt;
import org.aya.syntax.ref.AnyVar;
import org.aya.syntax.ref.GeneralizedVar;
import org.aya.syntax.ref.LocalVar;
import org.aya.util.position.SourcePos;
import org.aya.util.position.WithPos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/// ContextWalker traversal the concrete syntax tree to target position, record all available variable.
/// It behaves like a [org.aya.resolve.visitor.ExprResolver]
public class ContextWalker implements SyntaxNodeAction.Cursor {
  private static final @NotNull LocalVar RESULT_VAR = new LocalVar("_", SourcePos.NONE);

  private final @NotNull MutableMap<String, Completion.CompletionItemu.Local> localContext;
  private final @NotNull MutableList<String> moduleContext;
  private final @NotNull XY xy;

  public ContextWalker(@NotNull XY xy) {
    this.xy = xy;
    this.localContext = MutableMap.create();
    this.moduleContext = MutableList.create();
  }

  // region Context Restriction

  @Override
  public @NotNull XY location() {
    return xy;
  }

  @Override
  public void doAccept(@NotNull Stmt stmt) {
    if (stmt instanceof Command.Module module) {
      moduleContext.append(module.name());
    }

    Cursor.super.doAccept(stmt);
  }

  @Override
  public void visitClause(Pattern.@NotNull Clause clause) {
    if (!accept(location(), clause.sourcePos)) return;
    Cursor.super.visitClause(clause);
  }

  @Override
  public void visitLetBind(Expr.@NotNull LetBind bind) {
    if (!accept(location(), bind.sourcePos())) return;
    Cursor.super.visitLetBind(bind);
  }

  @Override
  public void visitLetBody(Expr.@NotNull Let let) {
    if (!accept(location(), let.body().sourcePos())) return;
    Cursor.super.visitLetBody(let);
  }

  @Override
  public void visitTelescope(@NotNull SeqView<Expr.Param> params, @Nullable WithPos<Expr> result) {
    var telescope = params;

    // in order to [indexWhere]
    if (result != null) telescope = telescope.appended(new Expr.Param(result.sourcePos(), RESULT_VAR, result, true));

    var idx = telescope.indexWhere(it -> accept(xy, it.sourcePos()));
    if (idx != -1) {
      telescope = telescope.take(idx);
    }

    // the key is skipping the variable that are not accessible, the expr doesn't matter,
    // as they will be skipped by [visitExpr] if the cursor is not inside
    Cursor.super.visitTelescope(telescope, result);
  }

  @Override
  public void visitDoBinds(@NotNull SeqView<Expr.DoBind> binds) {
    // similar to visitTelescope
    var idx = binds.indexWhere(it -> accept(xy, it.sourcePos()));
    if (idx != -1) {
      var bindSeq = binds.take(idx + 1).toSeq();
      binds = bindSeq.view().take(idx);

      var body = bindSeq.get(idx).expr();
      binds = binds.appended(new Expr.DoBind(body));
    }

    Cursor.super.visitDoBinds(binds);
  }

  @Override
  public void visitMatch(@NotNull Expr.Match match) {
    var discriminant = match.discriminant();
    var returns = match.returns();

    discriminant.forEach(it -> visitExpr(it.discr().sourcePos(), it.discr().data()));

    if (returns != null && accept(xy, returns.sourcePos())) {
      // not available in clause bodies!
      discriminant.view()
        .mapNotNull(Expr.Match.Discriminant::asBinding)
        .forEach(it -> visitLocalVarDecl(it, Type.noType));
      visitExpr(returns.sourcePos(), returns.data());
    } else {
      match.clauses().forEach(this::visitClause);
    }
  }

  // endregion Context Restriction

  @Override
  public void visitVarDecl(@NotNull SourcePos pos, @NotNull AnyVar var, @NotNull Type type) {
    if (var == LocalVar.IGNORED || pos == SourcePos.NONE) return;
    // in case of the resolving failed.
    if (!(var instanceof LocalVar || var instanceof GeneralizedVar)) return;
    this.localContext.put(var.name(), new Completion.CompletionItemu.Local(var, type));
  }

  /// @return all accessible local variables and their concrete types. The order is not guaranteed.
  public @NotNull ImmutableSeq<Completion.CompletionItemu.Local> localContext() {
    return localContext.valuesView().toSeq();
  }

  /// @return which module the cursor in
  public @NotNull ModuleName moduleContext() {
    return ModuleName.from(moduleContext);
  }
}
