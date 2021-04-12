// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.lsp.highlight;

import org.aya.api.error.SourcePos;
import org.jetbrains.annotations.NotNull;

public record Symbol(
  @NotNull SourcePos sourcePos,
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
    Param,
    Operator,
    FnCall,
    DataCall,
    StructCall,
    ConCall,
    FieldCall,
  }
}
