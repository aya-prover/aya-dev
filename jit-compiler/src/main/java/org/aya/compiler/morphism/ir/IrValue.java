// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.morphism.ir;

import org.aya.pretty.doc.Docile;

/// Immediate values in the ANF
public sealed interface IrValue extends Docile
  permits IrVariable, IrExpr.Const {
}
