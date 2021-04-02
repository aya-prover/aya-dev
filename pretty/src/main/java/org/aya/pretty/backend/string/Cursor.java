// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.pretty.backend.string;

import org.jetbrains.annotations.NotNull;

public class Cursor {
  int cursor;
  int lineStartCursor;
  StringBuilder builder;

  public Cursor(@NotNull StringBuilder builder) {
    this.builder = builder;
  }

  public void indent(int indent) {
    if (indent > 0) cursor += indent;
  }

  public void visibleContent(@NotNull Runnable r) {
    var prev = builder.length();
    r.run();
    var now = builder.length();
    cursor += Math.max(0, now - prev);
  }

  public boolean isAtLineStart() {
    return cursor == lineStartCursor;
  }

  public void movedToNewLine() {
    cursor = lineStartCursor = 0;
  }
}
