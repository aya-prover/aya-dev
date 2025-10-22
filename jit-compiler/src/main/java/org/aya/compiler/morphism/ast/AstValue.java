// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.morphism.ast;

/// Immediate values in the ANF
public sealed interface AstValue
  permits AstVariable, AstExpr.Const {
}
