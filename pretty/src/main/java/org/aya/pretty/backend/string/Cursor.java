// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.pretty.backend.string;

import org.jetbrains.annotations.NotNull;

public class Cursor {
  public interface CursorAPI {
    @NotNull String makeIndent(int indent);
  }

  int cursor;
  int lineStartCursor;
  int nestLevel;
  StringBuilder builder = new StringBuilder();
  CursorAPI api;

  public Cursor(CursorAPI api) {
    this.api = api;
  }

  public void content(@NotNull CharSequence content, boolean visible) {
    if (visible) visibleContent(content);
    else invisibleContent(content);
  }

  public void invisibleContent(@NotNull CharSequence content) {
    checkLineStart();
    builder.append(content);
  }

  public void visibleContent(@NotNull CharSequence content) {
    checkLineStart();
    builder.append(content);
    moveForward(content.length());
  }

  private void checkLineStart() {
    if (isAtLineStart()) {
      builder.append(api.makeIndent(nestLevel));
      moveForward(nestLevel);
    }
  }

  public void lineBreakWith(@NotNull CharSequence lineBreak) {
    visibleContent(lineBreak);
    moveToNewLine();
  }

  public boolean isAtLineStart() {
    return cursor == lineStartCursor;
  }

  public void moveToNewLine() {
    cursor = lineStartCursor = 0;
  }

  public void moveForward(int count) {
    cursor += Math.max(0, count);
  }

  public void nested(int nest, @NotNull Runnable r) {
    nestLevel += nest;
    r.run();
    nestLevel -= nest;
  }
}
