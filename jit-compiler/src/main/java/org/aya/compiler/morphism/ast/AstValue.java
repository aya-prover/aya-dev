// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.morphism.ast;

import org.aya.pretty.doc.Docile;

/// Immediate values in the ANF
public sealed interface AstValue extends Docile
  permits AstVariable, AstExpr.Const {
}
