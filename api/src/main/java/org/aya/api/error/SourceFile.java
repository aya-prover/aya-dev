// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.api.error;

import org.glavo.kala.control.Option;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.util.Objects;

public record SourceFile(@NotNull Option<URI> file, @NotNull String sourceCode) {
  public static final SourceFile NONE = new SourceFile(Option.none(), "");

  public boolean isSomeFile() {
    return this != SourceFile.NONE && file.isDefined();
  }

  public @NotNull String name() {
    return file.map(Objects::toString).getOrDefault("<unknown-file>");
  }
}
