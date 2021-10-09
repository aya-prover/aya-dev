// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.lsp.utils;

import org.aya.api.error.SourcePos;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Objects;

public class LspRange {
  public static final Range NONE = new Range();

  public static @NotNull Range toRange(@NotNull SourcePos sourcePos) {
    if (sourcePos == SourcePos.NONE) return NONE;
    return new Range(new Position(sourcePos.startLine() - 1, sourcePos.startColumn()),
      new Position(sourcePos.endLine() - 1, sourcePos.endColumn() + 1));
  }

  public static @Nullable LocationLink toLoc(@NotNull SourcePos from, @NotNull SourcePos to) {
    var uri = from.file().path().map(Path::toUri).map(Objects::toString);
    if (uri.isEmpty()) return null;
    var fromRange = toRange(from);
    var toRange = toRange(to);
    return new LocationLink(uri.get(), toRange, toRange, fromRange);
  }
}
