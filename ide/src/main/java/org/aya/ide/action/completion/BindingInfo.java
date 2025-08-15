// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.ide.action.completion;

import kala.value.MutableValue;
import org.aya.syntax.concrete.Expr;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.ref.LocalVar;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/// @param typeExpr the **result** expr of [#var]
/// @param theCore  the type of [#var], not necessary the core of [#typeExpr], because expr can live without bindings, but term does not.
///                                This only affect [Expr.LetBind].
public record BindingInfo(@NotNull LocalVar var, @Nullable Expr typeExpr, @NotNull MutableValue<Term> theCore) {
  public static @NotNull BindingInfo from(@NotNull Expr.Param param) {
    return new BindingInfo(param.ref(), param.type(), param.theCoreType());
  }
}
