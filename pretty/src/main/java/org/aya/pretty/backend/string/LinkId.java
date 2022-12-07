// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.pretty.backend.string;

import org.jetbrains.annotations.NotNull;

import java.io.Serializable;

/**
 * @author imkiva
 */
public sealed interface LinkId extends Serializable {
  @NotNull String normalize();

  record FromString(@NotNull String normalize) implements LinkId {
  }

  record FromInt(int id) implements LinkId {
    @Override public @NotNull String normalize() {
      // https://stackoverflow.com/a/37271406/9506898
      return "x" + id;
    }
  }
}
