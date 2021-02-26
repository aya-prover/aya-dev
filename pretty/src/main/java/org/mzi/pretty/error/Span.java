// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.mzi.pretty.error;

import org.jetbrains.annotations.NotNull;

public interface Span {
  @NotNull String input();

  @NotNull Span.StartStopLineCol findStartStopLineCol(PrettyErrorConfig config);

  record StartStopLineCol(
    int startLine,
    int startCol,
    int endLine,
    int endCol) {
  }
}
