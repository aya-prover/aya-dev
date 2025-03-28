// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.util.error;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import kala.control.Option;
import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Docile;
import org.aya.util.position.SourcePos;
import org.aya.util.position.SourcePos.NowLoc;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;

/// @author kiva
public record PrettyError(
  @NotNull SourcePos errorRange,
  @NotNull Doc brief,
  @NotNull FormatConfig formatConfig,
  @NotNull ImmutableSeq<PosDoc> inlineHints
) implements Docile {
  public record PosDoc(
    @NotNull SourcePos pos,
    @NotNull Doc doc
  ) { }

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
    public @NotNull String underlineBody(int n) { return Character.toString(underlineBody).repeat(n); }
    public @NotNull Doc underlineBodyDoc(int n) { return Doc.plain(underlineBody(n)); }
    public @NotNull Doc lineNoSepDoc() { return Doc.plain(Character.toString(lineNoSeparator)); }
    public @NotNull Doc beginCornerDoc() { return Doc.plain(Character.toString(beginCorner)); }
    public @NotNull Doc endCornerDoc() { return Doc.plain(Character.toString(endCorner)); }

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
      Option.some('╯'),
      '╰',
      '╯',
      '─',
      '╭',
      '╰'
    );
  }

  enum MultilineMode {
    DISABLED,
    START,
    END,
  }

  public @NotNull Doc toDoc(@NotNull PrettyErrorConfig config) {
    var primary = errorRange;
    var hints = inlineHints.view()
      .filter(kv -> kv.pos.oneLinear())
      .toSeq(); // TODO: multiline inline hints?
    var allRange = hints.map(PosDoc::pos).foldLeft(primary, SourcePos::union);
    return Doc.vcat(
      Doc.plain("In file " + errorRange.file().display() + ":" + primary.startLine() + ":" + primary.startColumn() + " ->"),
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
      lineDocs.append(Doc.ONE_WS); // cannot use `empty()` or `plain("")`
      codeDocs.append(code);
    }
  }

  private static final class HintGroup {
    @NotNull MutableList<Doc> underlines = MutableList.create();
    @NotNull MutableList<Doc> notes = MutableList.create();
    @NotNull MutableList<HintLine> overlapped = MutableList.create();
    @Nullable HintLine startOrEnd = null;

    public void add(int indent, @NotNull HintLine line, @NotNull PrettyError.FormatConfig cfg) {
      var hint = renderHint(cfg, line.startCol, line.endCol, switch (line.loc) {
        case Shot, Between, None -> PrettyError.MultilineMode.DISABLED;
        case Start -> PrettyError.MultilineMode.START;
        case End -> PrettyError.MultilineMode.END;
      });
      underlines.append(Doc.indent(indent, hint));
      notes.append(line.note);
      if (line.loc == NowLoc.Start || line.loc == NowLoc.End)
        startOrEnd = line;
    }
  }

  record PreHint(Doc doc, SourcePos pos, int allocIndent) { }
  record HintLine(Doc note, NowLoc loc, int startCol, int endCol, int allocIndent) { }

  private static final int INDENT_FACTOR = 2;

  /** invariant: `others` is sorted by `startCol` */
  private @NotNull HintGroup splitHints(@NotNull ImmutableSeq<HintLine> others) {
    var group = new HintGroup();
    if (others.isNotEmpty()) {
      var first = others.getFirst();
      group.add(first.startCol, first, formatConfig);
      int left = first.startCol;
      int right = first.endCol;
      int last = right;
      for (int i = 1; i < others.size(); ++i) {
        var the = others.get(i);
        if (overlaps(left, right, the.startCol, the.endCol)) {
          group.overlapped.append(the);
        } else {
          var indent = the.startCol - last;
          group.add(indent, the, formatConfig);
          last = the.endCol;
          left = Math.min(left, the.startCol);
          right = Math.max(right, the.endCol);
        }
      }
    }
    return group;
  }

  private void renderHints(
    boolean continued,
    boolean continuedFromStartEnd,
    int currentLine, int codeIndent,
    int vbarUsedIndent, @NotNull Doc vbar,
    @NotNull Doc currentCode,
    @NotNull CodeBuilder builder,
    @NotNull ImmutableSeq<HintLine> others
  ) {
    var split = splitHints(others);
    var startOrEnd = split.startOrEnd;
    var rest = codeIndent - vbarUsedIndent;
    // commit code
    if (!continued) {
      var codeDoc = (startOrEnd == null || startOrEnd.loc != NowLoc.End)
        ? Doc.cat(vbar, Doc.indent(rest * INDENT_FACTOR, currentCode))
        : Doc.cat(vbar, formatConfig.lineNoSepDoc(), Doc.indent(rest * INDENT_FACTOR - 1, currentCode));
      builder.add(currentLine, codeDoc);
    }
    // commit hint
    var underlines = Doc.cat(split.underlines);
    var notes = Doc.cat(split.notes);
    var almost = notes.isEmpty() || (notes instanceof Doc.Cat(var inner) && inner.isEmpty())
      ? underlines : Doc.stickySep(underlines, Doc.align(notes));
    var codeHint = startOrEnd != null
      ? renderStartEndHint(startOrEnd, vbar, almost, rest)
      : renderContinuedHint(continued, continuedFromStartEnd, vbar, almost, rest);
    builder.add(codeHint);
    // show overlapped hints in the next line
    if (split.overlapped.isNotEmpty())
      renderHints(true, startOrEnd != null, currentLine, codeIndent, vbarUsedIndent, vbar, currentCode, builder, split.overlapped.toSeq());
  }

  private @NotNull Doc renderStartEndHint(@NotNull HintLine startOrEnd, @NotNull Doc vbar, @NotNull Doc almost, int rest) {
    return startOrEnd.loc == NowLoc.Start
      ? Doc.cat(vbar, formatConfig.beginCornerDoc(), formatConfig.underlineBodyDoc(rest * INDENT_FACTOR - 1), almost)
      : Doc.cat(vbar, formatConfig.endCornerDoc(), formatConfig.underlineBodyDoc(rest * INDENT_FACTOR - 1), almost);
  }

  private @NotNull Doc renderContinuedHint(boolean continued, boolean fromStartEnd, @NotNull Doc vbar, @NotNull Doc almost, int rest) {
    return continued && fromStartEnd // implies vbar.isEmpty()
      ? Doc.cat(formatConfig.lineNoSepDoc(), Doc.indent(rest * INDENT_FACTOR, almost))
      : Doc.cat(vbar, Doc.indent(rest * INDENT_FACTOR, almost));
  }

  record VBar(int last, Doc doc) { }
  private VBar computeMultilineVBar(@NotNull ImmutableSeq<HintLine> between) {
    int last = 0;
    var vbar = Doc.empty();
    for (var the : between) {
      var level = the.allocIndent - last;
      vbar = Doc.cat(vbar, Doc.indent(level * INDENT_FACTOR, formatConfig.lineNoSepDoc()));
      last = the.endCol;
    }
    return new VBar(last, vbar);
  }

  private void renderHints(
    int currentLine, int codeIndent,
    @NotNull Doc currentCode,
    @NotNull CodeBuilder builder,
    @NotNull ImmutableSeq<HintLine> hintLines
  ) {
    var between = hintLines.view()
      .filter(h -> h.loc == NowLoc.Between)
      .sorted(Comparator.comparingInt(a -> a.allocIndent))
      .toSeq();
    var others = hintLines.filter(h -> h.loc != NowLoc.Between);
    var vbar = computeMultilineVBar(between);
    renderHints(false, false, currentLine, codeIndent, vbar.last, vbar.doc, currentCode, builder, others);
  }

  private @NotNull Doc visualizeCode(
    @NotNull PrettyErrorConfig config, @NotNull SourcePos fullRange,
    @NotNull SourcePos primaryRange, @NotNull ImmutableSeq<PosDoc> hints
  ) {
    int startLine = fullRange.startLine();
    int endLine = fullRange.endLine();
    int showMore = config.showMore();

    // calculate the maximum char width of line number
    int linenoWidth = Math.max(widthOfLineNumber(startLine), widthOfLineNumber(endLine)) + 1;

    // collect lines from (startLine - SHOW_MORE_LINE) to (endLine + SHOW_MORE_LINE)
    var lines = errorRange.file()
      .sourceCode()
      .lines()
      .skip(Math.max(startLine - 1 - showMore, 0))
      .limit(endLine - startLine + 1 + showMore)
      .map(line -> visualizeLine(config, line))
      .toList();

    final int minLineNo = Math.max(startLine - showMore, 1);
    final int maxLineNo = minLineNo + lines.size();

    // allocate hint nest
    var alloc = MutableList.<PreHint>create();
    alloc.append(new PreHint(Doc.empty(), primaryRange, 0));
    hints.view()
      .filter(kv -> kv.pos.startLine() >= minLineNo && kv.pos.endLine() <= maxLineNo)
      .mapIndexed((i, kv) -> new PreHint(kv.doc, kv.pos, i + 1))
      .forEach(alloc::append);

    int codeIndent = alloc.size();
    int lineNo = minLineNo;
    var builder = new CodeBuilder();

    for (var line : lines) {
      final int currentLine = lineNo;
      final var currentCode = Doc.plain(line);

      var hintLines = alloc.view()
        .mapNotNull(note -> switch (note.pos.nowLoc(currentLine)) {
          case Shot ->
            new HintLine(note.doc, NowLoc.Shot, note.pos.startColumn(), note.pos.endColumn(), note.allocIndent);
          case Start -> new HintLine(Doc.empty(), NowLoc.Start, 0, note.pos.startColumn(), note.allocIndent);
          case End -> new HintLine(note.doc, NowLoc.End, 0, note.pos.endColumn(), note.allocIndent);
          case Between -> new HintLine(Doc.empty(), NowLoc.Between, 0, 0, note.allocIndent);
          case None -> null;
        })
        .sorted(Comparator.comparingInt(a -> a.startCol))
        .toSeq();

      if (hintLines.isEmpty()) {
        builder.add(currentLine, Doc.indent(codeIndent * INDENT_FACTOR, currentCode));
      } else {
        renderHints(currentLine, codeIndent, currentCode, builder, hintLines);
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

  private static @NotNull Doc renderHint(@NotNull PrettyError.FormatConfig cfg, int startCol, int endCol, PrettyError.MultilineMode mode) {
    var builder = new StringBuilder();
    if (mode == PrettyError.MultilineMode.DISABLED || cfg.errorIndicator().isEmpty()) {
      builder.append(cfg.underlineBegin());
      // -1 for the start symbol
      int length = endCol - startCol - 1;
      if (length > 0) {
        // endCol is in the next line
        builder.append(cfg.underlineBody(length));
      }
      builder.append(cfg.underlineEnd());
    } else {
      int length = endCol - startCol;
      if (length > 0) {
        // endCol is in the next line
        builder.append(cfg.underlineBody(length));
      }
      builder.append(cfg.errorIndicator().get());
    }
    return Doc.plain(builder.toString());
  }

  private int widthOfLineNumber(int line) {
    return String.valueOf(line).length();
  }
}
