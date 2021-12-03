// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.api.error;

import org.aya.api.distill.DistillerOptions;
import org.aya.pretty.doc.Doc;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000
 */
public interface Reporter {
  /**
   * Report a problem
   *
   * @param problem problem to report
   */
  void report(@NotNull Problem problem);

  @ApiStatus.Internal
  default void reportString(@NotNull String s) {
    reportDoc(Doc.english(s));
  }

  @ApiStatus.Internal
  default void reportNest(@NotNull String text, int indent) {
    reportDoc(Doc.nest(indent, Doc.english(text)));
  }

  @ApiStatus.Internal
  default void reportDoc(@NotNull Doc doc) {
    report(new Problem() {
      @Override public @NotNull SourcePos sourcePos() {
        return SourcePos.NONE;
      }

      @Override public @NotNull Severity level() {
        return Severity.INFO;
      }

      @Override public @NotNull Doc describe(@NotNull DistillerOptions options) {
        return doc;
      }
    });
  }
}
