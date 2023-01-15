// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.stmt.decl;

import org.jetbrains.annotations.NotNull;

/**
 * Common parts of concrete definitions.
 * In particular, it does not assume the definition to have a telescope.
 *
 * @author ice1000
 * @apiNote This class should only be used in extends and permits clause. Use {@link Decl} elsewhere instead.
 * @see Decl
 */
public sealed abstract class CommonDecl implements Decl permits ClassDecl, TeleDecl {
  public final @NotNull DeclInfo info;

  protected CommonDecl(@NotNull DeclInfo info) {
    this.info = info;
  }

  @Override public @NotNull DeclInfo info() {
    return info;
  }

  @Override public String toString() {
    return getClass().getSimpleName() + "[" + ref().name() + "]";
  }
}
