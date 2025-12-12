// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve.ser;

import org.aya.syntax.concrete.stmt.UseHide;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;

/// @see UseHide.Rename
public record SerRename(@NotNull SerQualifiedID qid, @NotNull String to) implements Serializable {
  public static @NotNull SerRename from(@NotNull UseHide.Rename rename) {
    return new SerRename(SerQualifiedID.from(rename.name()), rename.to());
  }
  public @NotNull UseHide.Rename make() { return new UseHide.Rename(qid.make(), to); }
}
