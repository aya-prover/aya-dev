// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.pretty.error;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import kala.tuple.Tuple;
import kala.tuple.Tuple2;
import kala.tuple.Tuple3;
import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Docile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;

/**
 * @author kiva
 */
public record PrettyErrorDoc(
  @NotNull String filePath,
  @NotNull Span errorRange,
  @NotNull Doc brief,
  @NotNull PrettyError.FormatConfig formatConfig,
  @NotNull ImmutableSeq<Tuple2<Span, Doc>> inlineHints
) implements Docile {
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
      Doc.empty(),
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

  private static final class CodeBuilder {
    private final @NotNull MutableList<Doc> lineDocs = MutableList.create();
    private final @NotNull MutableList<Doc> codeDocs = MutableList.create();

    void add(int currentLine, @NotNull Doc code) {
      lineDocs.append(Doc.plain(String.valueOf(currentLine)));
      codeDocs.append(code);
    }

    void add(@NotNull Doc code) {
      lineDocs.append(Doc.plain(" ")); // cannot use `empty()` or `plain("")`
      codeDocs.append(code);
    }
  }

  record HintLine(Doc note, Span.NowLoc loc, int startCol, int endCol, int allocIndent) {}

  record HintSplit(@NotNull ImmutableSeq<Doc> underlines, @NotNull ImmutableSeq<Doc> notes,
                   @NotNull ImmutableSeq<HintLine> overlapped, @Nullable HintLine startOrEnd) {}

  private @NotNull HintSplit splitHints(
    @NotNull ImmutableSeq<HintLine> others
  ) {
    var overlapped = MutableList.<HintLine>create();
    var underlines = MutableList.<Doc>create();
    var notes = MutableList.<Doc>create();
    HintLine startOrEnd = null;
    if (others.isNotEmpty()) {
      var first = others.first();
      underlines.append(renderHint(first.startCol, first.endCol, mode(first.loc)));
      notes.append(first.note);
      if (first.loc == Span.NowLoc.Start || first.loc == Span.NowLoc.End) startOrEnd = first;
      int left = first.startCol;
      int right = first.endCol;
      int last = right;
      for (int i = 1; i < others.size(); ++i) {
        var the = others.get(i);
        if (overlaps(left, right, the.startCol, the.endCol)) {
          overlapped.append(the);
        } else {
          if (the.loc == Span.NowLoc.Start || the.loc == Span.NowLoc.End) startOrEnd = the;
          var indent = the.startCol - last;
          underlines.append(Doc.indent(indent, renderHint(the.startCol, the.endCol, mode(the.loc))));
          notes.append(the.note);
          last = the.endCol;
          left = Math.min(left, the.startCol);
          right = Math.max(right, the.endCol);
        }
      }
    }
    return new HintSplit(underlines.toImmutableSeq(), notes.toImmutableSeq(), overlapped.toImmutableSeq(), startOrEnd);
  }

  private void buildHints(
    int currentLine, int codeIndent, int vbarUsedIndent,
    @NotNull Doc vbar,
    @NotNull String currentCode,
    @NotNull CodeBuilder builder,
    @NotNull ImmutableSeq<HintLine> others
  ) {
    var split = splitHints(others);
    var rest = codeIndent - vbarUsedIndent;
    // commit code
    var codeDoc = (split.startOrEnd == null || split.startOrEnd.loc != Span.NowLoc.End)
      ? Doc.cat(vbar, Doc.indent(rest * 2, Doc.plain(currentCode)))
      : Doc.cat(vbar, formatConfig.lineNoSepDoc(),
        Doc.indent(rest * 2 - 1, Doc.plain(currentCode)));
    builder.add(currentLine, codeDoc);
    // commit hint
    var almost = Doc.stickySep(Doc.cat(split.underlines), Doc.cat(split.notes));
    var codeHint = split.startOrEnd == null
      ? Doc.cat(vbar, Doc.indent(rest * 2, almost))
      : split.startOrEnd.loc == Span.NowLoc.Start
        ? Doc.cat(vbar, formatConfig.beginCornerDoc(), formatConfig.underlineBodyDoc(rest * 2 - 1), almost)
        : Doc.cat(vbar, formatConfig.endCornerDoc(), formatConfig.underlineBodyDoc(rest * 2 - 1), almost);
    builder.add(codeHint);
    // show overlapped hints in the next line
    if (split.overlapped.isNotEmpty()) {
      buildHints(currentLine, codeIndent, vbarUsedIndent, vbar, currentCode, builder, split.overlapped);
    }
  }

  private Tuple2<Integer, Doc> computeBetweenVBar(@NotNull ImmutableSeq<HintLine> between) {
    int last = 0;
    var vbar = Doc.empty();
    for (var the : between) {
      var level = the.allocIndent - last;
      vbar = Doc.cat(vbar, Doc.indent(level * 2, formatConfig.lineNoSepDoc()));
      last = the.endCol;
    }
    return Tuple.of(last, vbar);
  }

  private void buildHints(
    int currentLine, int codeIndent,
    @NotNull String currentCode,
    @NotNull CodeBuilder builder,
    @NotNull ImmutableSeq<HintLine> hintLines
  ) {
    var between = hintLines.filter(h -> h.loc == Span.NowLoc.Between)
      .sorted(Comparator.comparingInt(a -> a.allocIndent));
    var others = hintLines.filter(h -> h.loc != Span.NowLoc.Between);
    var vbar = computeBetweenVBar(between);
    buildHints(currentLine, codeIndent, vbar._1, vbar._2, currentCode, builder, others);
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

    final int minLineNo = Math.max(startLine - showMore, 1);
    final int maxLineNo = minLineNo + lines.size();

    // allocate hint nest
    var alloc = MutableList.<Tuple3<Span.Data, Doc, Integer>>create();
    alloc.append(Tuple.of(primaryRange, Doc.empty(), 0));
    hints.view()
      .filter(kv -> kv._1.startLine() >= minLineNo && kv._1.endLine() <= maxLineNo)
      .mapIndexed((i, kv) -> Tuple.of(kv._1, kv._2, i + 1))
      .forEach(alloc::append);

    int codeIndent = alloc.size();
    int lineNo = minLineNo;
    var builder = new CodeBuilder();

    for (var line : lines) {
      final int codeBegin = codeIndent * 2;
      final int currentLine = lineNo;

      var hintLines = MutableList.<HintLine>create();
      alloc.view()
        .mapNotNull(note -> switch (note._1.nowLoc(currentLine)) {
          case Shot -> new HintLine(note._2, Span.NowLoc.Shot, note._1.startCol(), note._1.endCol(), note._3);
          case Start -> new HintLine(Doc.empty(), Span.NowLoc.Start, 0, note._1.startCol(), note._3);
          case End -> new HintLine(note._2, Span.NowLoc.End, 0, note._1.endCol(), note._3);
          case Between -> new HintLine(Doc.empty(), Span.NowLoc.Between, 0, 0, note._3);
          case None -> null;
        })
        .forEach(hintLines::append);

      hintLines.sort(Comparator.comparingInt(a -> a.startCol));
      if (hintLines.isEmpty()) {
        builder.add(currentLine, Doc.indent(codeBegin, Doc.plain(line)));
      } else {
        buildHints(currentLine, codeIndent, line, builder, hintLines.toImmutableSeq());
      }
      lineNo++;
    }

    return Doc.catBlockR(linenoWidth,
      builder.lineDocs,
      Doc.cat(formatConfig.lineNoSepDoc(), Doc.ONE_WS),
      builder.codeDocs);
  }

  private static boolean overlaps(int x1, int x2, int y1, int y2) {
    // assuming x1 <= x2, y1 <= y2
    return x1 <= y2 && y1 <= x2;
  }

  private static PrettyError.MultilineMode mode(@NotNull Span.NowLoc loc) {
    return switch (loc) {
      case Shot, Between, None -> PrettyError.MultilineMode.DISABLED;
      case Start -> PrettyError.MultilineMode.START;
      case End -> PrettyError.MultilineMode.END;
    };
  }

  private Doc renderHint(int startCol, int endCol, PrettyError.MultilineMode mode) {
    var builder = new StringBuilder();
    if (mode == PrettyError.MultilineMode.DISABLED || formatConfig.errorIndicator().isEmpty()) {
      builder.append(formatConfig.underlineBegin());
      // -1 for the start symbol
      int length = endCol - startCol - 1;
      if (length > 0) {
        // endCol is in the next line
        builder.append(formatConfig.underlineBody(length));
      }
      builder.append(formatConfig.underlineEnd());
    } else {
      int length = endCol - startCol;
      if (length > 0) {
        // endCol is in the next line
        builder.append(formatConfig.underlineBody(length));
      }
      builder.append(formatConfig.errorIndicator().get());
    }
    return Doc.indent(startCol, Doc.plain(builder.toString()));
  }

  private int widthOfLineNumber(int line) {
    return String.valueOf(line).length();
  }
}
