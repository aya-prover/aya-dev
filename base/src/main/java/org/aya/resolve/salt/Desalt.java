// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve.salt;

import org.aya.prettier.AyaPrettierOptions;
import org.aya.resolve.ResolveInfo;
import org.aya.syntax.concrete.Expr;
import org.aya.util.position.PosedUnaryOperator;
import org.aya.util.position.SourcePos;
import org.jetbrains.annotations.NotNull;

/** Desugar, but the sugars are not sweet enough, therefore called salt. */
public record Desalt(
  @NotNull ResolveInfo info
) implements PosedUnaryOperator<Expr> {

  @Override
  public Expr apply(SourcePos sourcePos, Expr expr) {
    // TODO: check if this induces redundant descent
    expr = new DesugarMisc(info).apply(sourcePos, expr);
    expr = new DesugarLambdaHole().apply(sourcePos, expr);

    var debug = expr.toDoc(AyaPrettierOptions.debug());
    System.out.println(debug.renderToString(100, true));

    return expr;
  }
}
