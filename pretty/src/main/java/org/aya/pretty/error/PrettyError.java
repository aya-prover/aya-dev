// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.pretty.error;

import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Docile;
import org.glavo.kala.collection.mutable.Buffer;
import org.glavo.kala.control.Option;
import org.jetbrains.annotations.NotNull;

/**
 * @author kiva
 */
public record PrettyError(
  @NotNull String filePath,
  @NotNull Span errorRange,
  @NotNull Doc brief
) implements Docile {
  public Doc toDoc(PrettyErrorConfig config) {
    var lineCol = errorRange.normalize(config);

    return Doc.vcat(
      Doc.plain("In file " + filePath + ":" + lineCol.startLine() + ":" + lineCol.startCol() + " ->"),
      Doc.empty(),
      Doc.hang(2, visualizeCode(config, lineCol)),
      brief
    );
  }

  @Override public @NotNull Doc toDoc() {
    return toDoc(PrettyErrorConfig.DEFAULT);
  }

  private @NotNull String visualizeLine(PrettyErrorConfig config, String line) {
    int tabWidth = config.tabWidth();
    return line.replaceAll("\t", " ".repeat(tabWidth));
  }

  private @NotNull Doc visualizeCode(PrettyErrorConfig config, Span.Data lineCol) {
    int startLine = lineCol.startLine();
    int startCol = lineCol.startCol();
    int endLine = lineCol.endLine();
    int endCol = lineCol.endCol();
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
          int length = endCol - startCol - 1;
          if (length > 0) {
            // endCol is in the next line
            builder.append("-".repeat(length));
          }
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

  private int widthOfLineNumber(int line) {
    return String.valueOf(line).length();
  }
}
