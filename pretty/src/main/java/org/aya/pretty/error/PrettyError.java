// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.pretty.error;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import kala.control.Option;
import kala.tuple.Tuple;
import kala.tuple.Tuple2;
import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Docile;
import org.jetbrains.annotations.NotNull;

/**
 * @author kiva
 */
public record PrettyError(
  @NotNull String filePath,
  @NotNull Span errorRange,
  @NotNull Doc brief,
  @NotNull FormatConfig formatConfig,
  @NotNull ImmutableSeq<Tuple2<Span, Doc>> inlineHints
) implements Docile {

  public record FormatConfig(
    @NotNull Option<Character> vbarForHints,
    char lineNoSeparator,
    @NotNull Option<Character> errorIndicator,
    char underlineBegin,
    char underlineEnd,
    char underlineBody,
    char beginCorner,
    char endCorner
  ) {
    public @NotNull String underlineBody(int n) {
      return Character.toString(underlineBody).repeat(n);
    }

    public @NotNull Doc underlineBodyDoc(int n) {
      return Doc.plain(underlineBody(n));
    }

    public @NotNull Doc lineNoSepDoc() {
      return Doc.plain(Character.toString(lineNoSeparator));
    }

    public @NotNull Doc beginCornerDoc() {
      return Doc.plain(Character.toString(beginCorner));
    }

    public @NotNull Doc endCornerDoc() {
      return Doc.plain(Character.toString(endCorner));
    }

    public static final FormatConfig CLASSIC = new FormatConfig(
      Option.none(),
      '|',
      Option.none(),
      '^',
      '^',
      '-',
      '+',
      '+'
    );

    public static final FormatConfig UNICODE = new FormatConfig(
      Option.some('┝'),
      '│',
      Option.some('┘'),
      '╰',
      '╯',
      '─',
      '┌',
      '└'
    );
  }

  enum MultilineMode {
    DISABLED,
    START,
    END,
  }

  public @NotNull Doc toDoc(@NotNull PrettyErrorConfig config) {
    var primary = errorRange.normalize(config);
    var hints = inlineHints.view()
      .map(kv -> Tuple.of(kv._1.normalize(config), kv._2))
      .filter(kv -> kv._1.startLine() == kv._1.endLine())
      .toImmutableSeq(); // TODO: multiline inline hints?
    var allRange = hints.map(kv -> kv._1).foldLeft(primary, Span.Data::union);
    return Doc.vcat(
      Doc.plain("In file " + filePath + ":" + primary.startLine() + ":" + primary.startCol() + " ->"),
      Doc.empty(),
      Doc.hang(2, visualizeCode(config, allRange, primary, hints)),
      brief,
      Doc.empty()
    );
  }

  @Override public @NotNull Doc toDoc() {
    return toDoc(PrettyErrorConfig.DEFAULT);
  }

  private @NotNull String visualizeLine(@NotNull PrettyErrorConfig config, @NotNull String line) {
    int tabWidth = config.tabWidth();
    return line.replaceAll("\t", " ".repeat(tabWidth));
  }

  private @NotNull Doc visualizeCode(
    @NotNull PrettyErrorConfig config, @NotNull Span.Data fullRange,
    @NotNull Span.Data primaryRange, @NotNull ImmutableSeq<Tuple2<Span.Data, Doc>> hints
  ) {
    int startLine = fullRange.startLine();
    int endLine = fullRange.endLine();
    int showMore = config.showMore();

    // calculate the maximum char width of line number
    int linenoWidth = Math.max(widthOfLineNumber(startLine), widthOfLineNumber(endLine));

    // collect lines from (startLine - SHOW_MORE_LINE) to (endLine + SHOW_MORE_LINE)
    var lines = errorRange.input()
      .lines()
      .skip(Math.max(startLine - 1 - showMore, 0))
      .limit(endLine - startLine + 1 + showMore)
      .map(line -> visualizeLine(config, line))
      .collect(MutableList.factory());

    int lineNo = Math.max(startLine - showMore, 1);
    var docs = MutableList.<Doc>create();

    for (var line : lines) {
      docs.append(renderLine(line, lineNo, linenoWidth));

      // render error column as underlines
      final int finalLineNo = lineNo;
      hints.find(kv -> kv._1.startLine() == finalLineNo).ifDefined(find -> {
        int startCol = find._1.startCol();
        int endCol = find._1.endCol();
        var hintUnderline = renderHint(startCol, endCol, linenoWidth, MultilineMode.DISABLED);
        var doc = find._2;
        if (doc instanceof Doc.Empty) docs.append(hintUnderline);
        else docs.append(Doc.cat(hintUnderline, Doc.ONE_WS, doc));
      });
      // Inline hints are expected to be more informative. So we only
      // underline primary error part if there's no inline hints.
      if (hints.isEmpty()) {
        if (primaryRange.startLine() == primaryRange.endLine()) {
          if (lineNo == primaryRange.startLine()) {
            var hintUnderline = renderHint(primaryRange.startCol(), primaryRange.endCol(), linenoWidth, MultilineMode.DISABLED);
            docs.append(hintUnderline);
          }
        } else {
          if (lineNo == primaryRange.startLine()) {
            var hintUnderline = renderHint(primaryRange.startCol(), line.length(), linenoWidth, MultilineMode.START);
            docs.append(Doc.stickySep(hintUnderline, Doc.align(Doc.english("Begin of the error"))));
          } else if (lineNo == primaryRange.endLine()) {
            var hintUnderline = renderHint(0, primaryRange.endCol(), linenoWidth, MultilineMode.END);
            docs.append(Doc.stickySep(hintUnderline, Doc.align(Doc.english("End of the error"))));
          }
        }
      }
      lineNo++;
    }

    docs.append(Doc.empty());
    return Doc.vcat(docs);
  }

  private Doc renderHint(int startCol, int endCol, int linenoWidth, MultilineMode mode) {
    var builder = new StringBuilder();
    builder.append(" ".repeat(linenoWidth));
    if (formatConfig.vbarForHints().isDefined()) {
      builder.append(' ');
      builder.append(formatConfig.vbarForHints().get());
      builder.append(" ".repeat(startCol + 1));
    } else {
      builder.append(" ".repeat(startCol + " | ".length()));
    }

    if (mode == MultilineMode.DISABLED || formatConfig.errorIndicator().isEmpty()) {
      builder.append(formatConfig.underlineBegin());
      // -1 for the start symbol
      int length = endCol - startCol - 1;
      if (length > 0) {
        // endCol is in the next line
        builder.append(Character.toString(formatConfig.underlineBody()).repeat(length));
      }
      builder.append(formatConfig.underlineEnd());
    } else {
      int length = endCol - startCol;
      if (length > 0 && mode == MultilineMode.END) {
        // endCol is in the next line
        builder.append(" ".repeat(length));
      }
      builder.append(formatConfig.errorIndicator().get());
    }
    return Doc.plain(builder.toString());
  }

  private Doc renderLine(String line, int lineNo, int linenoWidth) {
    return renderLine(line, Option.ofNullable(lineNo), linenoWidth);
  }

  private Doc renderLine(String line, Option<Integer> lineNo, int linenoWidth) {
    var builder = new StringBuilder();
    if (lineNo.isDefined()) {
      builder.append(String.format("%" + linenoWidth + "d %c ", lineNo.get(), formatConfig.lineNoSeparator()));
    } else {
      builder.append(String.format("%" + linenoWidth + "s %c ", "", formatConfig.lineNoSeparator()));
    }
    builder.append(line);
    return Doc.plain(builder.toString());
  }

  private int widthOfLineNumber(int line) {
    return String.valueOf(line).length();
  }
}
