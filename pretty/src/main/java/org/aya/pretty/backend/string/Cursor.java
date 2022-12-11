// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.pretty.backend.string;

import org.jetbrains.annotations.NotNull;

public class Cursor {
  private int cursor;
  private int nestLevel;
  private int lineStartCursor;
  private final StringBuilder builder = new StringBuilder();
  private final StringPrinter<?> printer;

  public Cursor(StringPrinter<?> printer) {
    this.printer = printer;
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
      builder.append(printer.makeIndent(nestLevel));
      moveForward(nestLevel);
    }
  }

  /** Do something when I am not at line start. */
  public void whenLineUsed(@NotNull Runnable runnable) {
    if (!isAtLineStart()) runnable.run();
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
