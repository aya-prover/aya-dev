// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.pretty.error;

import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Docile;
import org.glavo.kala.collection.SeqLike;
import org.glavo.kala.collection.immutable.ImmutableMap;
import org.glavo.kala.collection.mutable.Buffer;
import org.glavo.kala.control.Option;
import org.glavo.kala.tuple.Tuple;
import org.glavo.kala.tuple.Tuple2;
import org.jetbrains.annotations.NotNull;

/**
 * @author kiva
 */
public record PrettyError(
  @NotNull String filePath,
  @NotNull Span errorRange,
  @NotNull Doc brief,
  @NotNull SeqLike<Tuple2<Span, Doc>> inlineHints
) implements Docile {
  public @NotNull Doc toDoc(@NotNull PrettyErrorConfig config) {
    var primary = errorRange.normalize(config);
    var hints = inlineHints.view()
      .map(kv -> Tuple.of(kv._1.normalize(config), kv._2))
      .<Span.Data, Doc>toImmutableMap();
    var full = hints.keysView().foldLeft(primary, Span.Data::union);

    return Doc.vcat(
      Doc.plain("In file " + filePath + ":" + primary.startLine() + ":" + primary.startCol() + " ->"),
      Doc.empty(),
      Doc.hang(2, visualizeCode(config, full, hints)),
      brief
    );
  }

  @Override public @NotNull Doc toDoc() {
    return toDoc(PrettyErrorConfig.DEFAULT);
  }

  private @NotNull String visualizeLine(@NotNull PrettyErrorConfig config, @NotNull String line) {
    int tabWidth = config.tabWidth();
    return line.replaceAll("\t", " ".repeat(tabWidth));
  }

  private @NotNull Doc visualizeCode(@NotNull PrettyErrorConfig config, @NotNull Span.Data fullRange,
                                     @NotNull ImmutableMap<Span.Data, Doc> hints) {
    int startLine = fullRange.startLine();
    int startCol = fullRange.startCol();
    int endLine = fullRange.endLine();
    int endCol = fullRange.endCol();
    int showMore = config.showMore();

    // calculate the maximum char width of line number
    int linenoWidth = Math.max(widthOfLineNumber(startLine), widthOfLineNumber(endLine));

    // collect lines from (startLine - SHOW_MORE_LINE) to (endLine + SHOW_MORE_LINE)
    var lines = errorRange.input()
      .lines()
      .skip(Math.max(startLine - 1 - showMore, 0))
      .limit(endLine - startLine + 1 + showMore)
      .map(line -> visualizeLine(config, line))
      .collect(Buffer.factory());

    var builder = new StringBuilder();
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
