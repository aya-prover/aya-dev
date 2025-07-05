// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.anf.ir.struct;

import org.jetbrains.annotations.NotNull;

/// Denotes the use of a variable.
public record IrVarRef(@NotNull IrVarDecl decl) {

}
