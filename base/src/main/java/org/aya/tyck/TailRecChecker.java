// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck;

import org.aya.syntax.concrete.Expr;
import org.aya.syntax.concrete.stmt.decl.FnDecl;
import org.aya.syntax.ref.DefVar;
import org.aya.tyck.error.TailRecError;
import org.aya.tyck.tycker.Problematic;
import org.aya.util.position.PosedUnaryOperator;
import org.aya.util.position.SourcePos;
import org.jetbrains.annotations.NotNull;

public class TailRecChecker implements PosedUnaryOperator<Expr> {
  private final Problematic reporter;
  private final FnDecl self;
  private boolean atTailPosition = true;

  public TailRecChecker(@NotNull Problematic reporter, @NotNull FnDecl self) {
    this.reporter = reporter;
    this.self = self;
  }

  @Override
  public Expr apply(SourcePos sourcePos, Expr expr) {
    var store = atTailPosition;
    switch (expr) {
      case Expr.App(var head, var args) -> {
        var func = head.data();
        if (func instanceof Expr.Ref ref && ref.var() instanceof DefVar<?, ?> defVar) {
          var fDecl = (FnDecl) defVar.concrete;
          if (fDecl == self && args.size() == self.telescope.size() && !atTailPosition) {
            reporter.fail(new TailRecError(sourcePos));
          }
        }
        atTailPosition = false;
        expr.descent(this);
      }
      case Expr.Let(var bind, var body) -> {
        atTailPosition = false;
        bind.descent(this);
        atTailPosition = store;
        body.descent(this);
      }
      case Expr.Match(var dis, var clauses, var returns) -> {
        atTailPosition = false;
        dis.forEach(disc -> disc.descent(this));
        clauses.forEach(clause -> clause.expr.forEach(e -> e.descent(this)));
        if (returns != null) returns.descent(this);
      }
      default -> {
        atTailPosition = false;
        expr.descent(this);
      }
    }
    atTailPosition = store;
    return null;
  }
}
