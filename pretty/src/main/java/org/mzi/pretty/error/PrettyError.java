// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.pretty.error;

import org.glavo.kala.Tuple;
import org.glavo.kala.Tuple4;
import org.glavo.kala.collection.mutable.Buffer;
import org.glavo.kala.control.Option;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mzi.pretty.doc.Doc;

/**
 * @author kiva
 */
public record PrettyError(
  @NotNull String filePath,
  @NotNull Span errorRange,
  @NotNull String errorMessage,
  @Nullable String noteMessage
) {
  private static final int SHOW_MORE_LINE = 2;

  public Doc toDoc() {
    var sourceRange = getSourceRange();
    var doc = Doc.vcat(
      Doc.plain("In file " + filePath + ":" + sourceRange._1 + ":" + sourceRange._2 + " -> "),
      Doc.plain(""),
      Doc.hang(2, visualizeCode(sourceRange)),
      Doc.plain("Error: " + errorMessage));
    return noteMessage != null
      ? Doc.vcat(doc, Doc.plain("note: " + noteMessage), Doc.empty())
      : Doc.vcat(doc, Doc.empty());
  }

  private @NotNull Doc visualizeCode(Tuple4<Integer, Integer, Integer, Integer> sourceRange) {
    int startLine = sourceRange._1;
    int startCol = sourceRange._2;
    int endLine = sourceRange._3;
    int endCol = sourceRange._4;
    Buffer<String> lines = errorRange.input()
      .lines()
      .skip(Math.max(startLine - 1 - SHOW_MORE_LINE, 0))
      .limit(endLine - startLine + 1 + SHOW_MORE_LINE)
      .collect(Buffer.factory());
    int linenoWidth = Math.max(widthOfLineNumber(startLine), widthOfLineNumber(endLine));

    StringBuilder builder = new StringBuilder();
    if (lines.sizeGreaterThanOrEquals(9)) {
      for (int i = 0; i < SHOW_MORE_LINE; ++i) {
        renderLine(builder, lines.get(i), Math.max(startLine + i - SHOW_MORE_LINE, 1), linenoWidth);
      }

      for (int i = 0; i < 3; ++i) {
        renderLine(builder, lines.get(i + SHOW_MORE_LINE), startLine + i, linenoWidth);
      }

      renderLine(builder, "...", Option.none(), linenoWidth);

      for (int i = 3; i > 0; --i) {
        renderLine(builder, lines.get(lines.size() - i), endLine - i + 1, linenoWidth);
      }

    } else {
      int lineNo = Math.max(startLine - SHOW_MORE_LINE, 1);
      for (String line : lines) {
        renderLine(builder, line, lineNo, linenoWidth);

        if (lineNo == startLine) {
          builder.append(" ".repeat(startCol + linenoWidth + " | ".length()));
          builder.append("^");
          builder.append("-".repeat(endCol - startCol - 1));
          builder.append("^");
          builder.append('\n');
        }

        lineNo++;
      }
    }

    return Doc.plain(builder.toString());
  }

  private void renderLine(StringBuilder builder, String line, int lineNo, int linenoWidth) {
    renderLine(builder, line, Option.of(lineNo), linenoWidth);
  }

  private void renderLine(StringBuilder builder, String line, Option<Integer> lineNo, int linenoWidth) {
    if (lineNo.isDefined()) {
      builder.append(String.format("%" + linenoWidth + "d | ", lineNo.get()));
    } else {
      builder.append(String.format("%" + linenoWidth + "s | ", ""));
    }
    builder.append(line);
    builder.append('\n');
  }

  private @NotNull Tuple4<Integer, Integer, Integer, Integer> getSourceRange() {
    String input = errorRange.input();
    int line = 1;
    int col = 0;
    int pos = 0;

    int startLine = -1;
    int startCol = -1;
    int endLine = -1;
    int endCol = -1;

    for (char c : input.toCharArray()) {
      pos++;
      switch (c) {
        case '\n' -> {
          line++;
          col = 0;
        }
        case '\t' -> col += 4;
        default -> col++;
      }

      if (pos == errorRange.start()) {
        startLine = line;
        startCol = col;
      } else if (pos == errorRange.end()) {
        endLine = line;
        endCol = col;
      }
    }

    return Tuple.of(startLine, startCol, endLine, endCol);
  }

  private int widthOfLineNumber(int line) {
    return String.valueOf(line).length();
  }
}
