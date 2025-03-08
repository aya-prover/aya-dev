// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.concrete;

import org.aya.syntax.ref.AnyVar;
import org.aya.util.position.SourcePos;
import org.jetbrains.annotations.NotNull;

/// A [Named] is a [AnyVar] introducer, such as
/// a [org.aya.syntax.concrete.stmt.decl.Decl], or a [org.aya.syntax.concrete.Expr.LetBind].
public interface Named {
  /// @return the source pos of the identifier, not the whole ast.
  @NotNull SourcePos nameSourcePos();
}
