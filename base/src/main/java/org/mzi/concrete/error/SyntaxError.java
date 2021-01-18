// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.concrete.error;

import org.jetbrains.annotations.NotNull;
import org.mzi.api.error.Problem;
import org.mzi.api.error.SourcePos;
import org.mzi.pretty.doc.Doc;

/**
 * @author kiva
 */
public record SyntaxError(
  @NotNull SourcePos sourcePos,
  @NotNull String reason
) implements Problem.Error {
  @Override
  public @NotNull Doc describe() {
    return Doc.cat(Doc.plain("Syntax error: "), Doc.plain(reason));
  }
}
