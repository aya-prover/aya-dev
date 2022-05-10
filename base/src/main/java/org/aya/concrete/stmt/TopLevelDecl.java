// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.stmt;

import org.aya.core.def.GenericDef;
import org.jetbrains.annotations.NotNull;

/**
 * Generic concrete definition, corresponding to {@link GenericDef}.
 *
 * @author zaoqi
 */
public sealed interface TopLevelDecl extends GenericDecl permits ClassDecl, Decl {
  enum Personality {
    NORMAL,
    EXAMPLE,
    COUNTEREXAMPLE,
  }

  @NotNull Personality personality();
}
