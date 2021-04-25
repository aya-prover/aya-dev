// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.lsp.highlight;

import org.eclipse.lsp4j.Range;
import org.jetbrains.annotations.NotNull;

public record Symbol(
  @NotNull Range range,
  @NotNull Kind kind
) {
  public enum Kind {
    // definitions
    ModuleDef,
    FnDef,
    DataDef,
    StructDef,
    ConDef,
    FieldDef,
    PrimDef,
    // expressions
    Generalize,
    FnCall,
    DataCall,
    StructCall,
    ConCall,
    FieldCall,
    PrimCall,
  }
}
