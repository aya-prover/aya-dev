// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.remark2;

import org.aya.pretty.doc.Doc;
import org.aya.util.distill.DistillerOptions;
import org.aya.util.error.SourcePos;
import org.aya.util.reporter.Problem;
import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000
 */
public record UnsupportedMarkdown(
  @Override @NotNull SourcePos sourcePos,
  @NotNull String nodeName
) implements Problem {
  @Override public @NotNull Severity level() {
    return Severity.WARN;
  }

  @Override public @NotNull Doc describe(@NotNull DistillerOptions options) {
    return Doc.english("Unsupported markdown syntax: " + nodeName + ".");
  }
}
