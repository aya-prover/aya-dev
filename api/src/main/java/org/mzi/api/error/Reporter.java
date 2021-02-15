// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.api.error;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.mzi.pretty.doc.Doc;

/**
 * @author ice1000
 */
public interface Reporter {
  /**
   * Report an problem
   *
   * @param problem problem to report
   */
  void report(@NotNull Problem problem);

  @ApiStatus.Internal
  default void reportString(@NotNull String s) {
    report(new Problem.Info() {
      @Override public @NotNull SourcePos sourcePos() {
        return SourcePos.NONE;
      }

      @Override public @NotNull Doc describe() {
        return Doc.plain(s);
      }
    });
  }
}
