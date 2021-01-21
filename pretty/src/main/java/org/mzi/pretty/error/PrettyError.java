// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.pretty.error;

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
  @NotNull Doc errorMessage,
  @NotNull Doc noteMessage
) {
  public Doc toDoc(PrettyErrorConfig config) {
    var sourceRange = getPrettyCode(config);
    var doc = Doc.vcat(
      Doc.plain("In file " + filePath + ":" + sourceRange.startLine + ":" + sourceRange.startCol + " -> "),
      Doc.empty(),
      Doc.hang(2, visualizeCode(sourceRange)),
      Doc.hsep(Doc.plain("Error:"), Doc.align(errorMessage))
    );
    return noteMessage instanceof Doc.Empty
      ? Doc.vcat(doc, Doc.empty())
      : Doc.vcat(doc, Doc.hsep(Doc.plain("note:"), Doc.align(noteMessage)), Doc.empty());
  }

  public Doc toDoc() {
    return toDoc(new PrettyErrorConfig.Default());
  }

  private @NotNull String visualizeLine(PrettyErrorConfig config, String line) {
    int tabWidth = config.tabWidth();
    return line.replaceAll("\t", " ".repeat(tabWidth));
  }

  private @NotNull Doc visualizeCode(PrettyCode prettyCode) {
    var config = prettyCode.prettyConfig;
    int startLine = prettyCode.startLine;
    int startCol = prettyCode.startCol;
    int endLine = prettyCode.endLine;
    int endCol = prettyCode.endCol;
    int showMore = config.showMore();

    // calculate the maximum char width of line number
    int linenoWidth = Math.max(widthOfLineNumber(startLine), widthOfLineNumber(endLine));

    // collect lines from (startLine - SHOW_MORE_LINE) to (endLine + SHOW_MORE_LINE)
    Buffer<String> lines = errorRange.input()
      .lines()
      .skip(Math.max(startLine - 1 - showMore, 0))
      .limit(endLine - startLine + 1 + showMore)
      .map(line -> visualizeLine(config, line))
      .collect(Buffer.factory());

    StringBuilder builder = new StringBuilder();

    // When there are too many lines of code, we only print
    // the first few lines and the last few lines, omitting the middle.
    if (lines.sizeGreaterThanOrEquals(9)) {
      // render SHOW_MORE_LINE before startLine
      for (int i = 0; i < showMore; ++i) {
        renderLine(builder, lines.get(i), Math.max(startLine + i - showMore, 1), linenoWidth);
      }

      // render first few lions from startLine
      for (int i = 0; i < 3; ++i) {
        renderLine(builder, lines.get(i + showMore), startLine + i, linenoWidth);
      }

      // omitting the middle
      renderLine(builder, "...", Option.none(), linenoWidth);

      // render last few lines before endLine
      for (int i = 3; i > 0; --i) {
        renderLine(builder, lines.get(lines.size() - i), endLine - i + 1, linenoWidth);
      }

    } else {
      // here we print all lines because the code is shorter.
      int lineNo = Math.max(startLine - showMore, 1);
      for (String line : lines) {
        renderLine(builder, line, lineNo, linenoWidth);

        // render error column as underlines
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

  private @NotNull PrettyError.PrettyCode getPrettyCode(PrettyErrorConfig config) {
    String input = errorRange.input();
    int line = 1;
    int col = 0;
    int pos = 0;

    int startLine = -1;
    int startCol = -1;
    int endLine = -1;
    int endCol = -1;

    int tabWidth = config.tabWidth();

    for (char c : input.toCharArray()) {
      pos++;
      switch (c) {
        case '\n' -> {
          line++;
          col = 0;
        }
        // treat tab as tabWidth-length-ed spaces
        case '\t' -> col += tabWidth;
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

    return new PrettyCode(config, startLine, startCol, endLine, endCol);
  }

  private int widthOfLineNumber(int line) {
    return String.valueOf(line).length();
  }

  private record PrettyCode(
    PrettyErrorConfig prettyConfig,
    int startLine,
    int startCol,
    int endLine,
    int endCol) {
  }
}
