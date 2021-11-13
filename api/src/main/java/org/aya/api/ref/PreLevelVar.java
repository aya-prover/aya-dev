// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.api.ref;

import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author ice1000
 * @apiNote used only in concrete syntax.
 * @param sourcePos null if this is a generalized level variable,
 *                  otherwise it denotes a to-be-solved level.
 */
public record PreLevelVar(@NotNull String name, @Nullable SourcePos sourcePos) implements Var {
  public PreLevelVar(@NotNull String name) {
    this(name, null);
  }
}
