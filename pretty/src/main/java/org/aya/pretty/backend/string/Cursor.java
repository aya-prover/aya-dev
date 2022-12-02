// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.pretty.backend.string;

import org.jetbrains.annotations.NotNull;

public class Cursor {
  @FunctionalInterface
  public interface CursorAPI {
    @NotNull String makeIndent(int indent);
  }

  private int cursor;
  private int nestLevel;
  private int lineStartCursor;
  private final StringBuilder builder = new StringBuilder();
  private final CursorAPI api;

  public Cursor(CursorAPI api) {
    this.api = api;
  }

  public @NotNull CharSequence result() {
    return builder;
  }

  public int getCursor() {
    return cursor;
  }

  public int getNestLevel() {
    return nestLevel;
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
    invisibleContent(content);
    moveForward(content.length());
  }

  private void checkLineStart() {
    if (isAtLineStart()) {
      builder.append(api.makeIndent(nestLevel));
      moveForward(nestLevel);
    }
  }

  public void lineBreakWith(@NotNull CharSequence lineBreak) {
    invisibleContent(lineBreak);
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
