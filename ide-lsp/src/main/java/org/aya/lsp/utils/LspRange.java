// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.lsp.utils;

import kala.control.Option;
import org.aya.ide.util.XY;
import org.aya.ide.util.XYXY;
import org.aya.util.error.SourcePos;
import org.javacs.lsp.Location;
import org.javacs.lsp.LocationLink;
import org.javacs.lsp.Position;
import org.javacs.lsp.Range;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.nio.file.Path;

public interface LspRange {
  @NotNull Range NONE = new Range();

  static @NotNull XY pos(@NotNull Position position) {
    return new XY(position.line + 1, position.character - 1);
  }

  static @NotNull XYXY range(@NotNull Range range) {
    return new XYXY(pos(range.start), pos(range.end));
  }

  static @NotNull Range toRange(@NotNull SourcePos sourcePos) {
    if (sourcePos == SourcePos.NONE) return NONE;
    return new Range(new Position(sourcePos.startLine() - 1, sourcePos.startColumn()),
      new Position(sourcePos.endLine() - 1, sourcePos.endColumn() + 1));
  }

  static @NotNull Option<URI> toFileUri(@NotNull SourcePos sourcePos) {
    return sourcePos.file().underlying().map(Path::toUri);
  }

  static @Nullable LocationLink toLoc(@NotNull SourcePos from, @NotNull SourcePos to) {
    var uri = toFileUri(to);
    if (uri.isEmpty()) return null;
    var fromRange = toRange(from);
    var toRange = toRange(to);
    return new LocationLink(fromRange, uri.get(), toRange, toRange);
  }

  static @Nullable Location toLoc(@NotNull SourcePos to) {
    var uri = toFileUri(to);
    if (uri.isEmpty()) return null;
    var toRange = toRange(to);
    return new Location(uri.get(), toRange);
  }
}
