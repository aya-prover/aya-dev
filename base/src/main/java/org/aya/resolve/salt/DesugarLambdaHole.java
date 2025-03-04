// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve.salt;

import org.aya.syntax.concrete.Expr;
import org.aya.util.position.PosedUnaryOperator;
import org.aya.util.position.SourcePos;

public final class DesugarLambdaHole implements PosedUnaryOperator<Expr> {
  @Override
  public Expr apply(SourcePos sourcePos, Expr expr) {
    return null;
  }
}
