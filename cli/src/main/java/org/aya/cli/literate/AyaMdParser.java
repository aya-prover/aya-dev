// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.literate;

import kala.collection.Seq;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import org.aya.concrete.GenericAyaParser;
import org.aya.concrete.remark.CodeAttrProcessor;
import org.aya.concrete.remark.CodeOptions;
import org.aya.concrete.remark.Literate;
import org.aya.concrete.remark.UnsupportedMarkdown;
import org.aya.generic.util.InternalException;
import org.aya.pretty.backend.md.MdStyle;
import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Style;
import org.aya.util.StringUtil;
import org.aya.util.error.SourceFile;
import org.aya.util.error.SourcePos;
import org.commonmark.node.*;
import org.commonmark.parser.IncludeSourceSpans;
import org.commonmark.parser.Parser;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AyaMdParser {
  public static final char LINE_SEPARATOR = '\n';
  private final @NotNull String code;

  /** For empty line that end with \n, the index points to \n */
  private final @NotNull ImmutableSeq<Integer> linesIndex;
  private final @NotNull SourceFile file;

  public AyaMdParser(@NotNull SourceFile file) {
    this.file = file;
    this.code = StringUtil.trimCRLF(file.sourceCode());
    this.linesIndex = StringUtil.indexedLines(code).map(x -> x._1);
  }

  public @NotNull Literate parseLiterate(@NotNull GenericAyaParser producer) {
    var parser = Parser.builder()
      .customDelimiterProcessor(CodeAttrProcessor.INSTANCE)
      .includeSourceSpans(IncludeSourceSpans.BLOCKS_AND_INLINES)
      .postProcessor(FillCodeBlock.INSTANCE)
      .build();
    return mapAST(parser.parse(code), producer);
  }

  /**
   * Extract all aya code blocks, keep source poses.
   * Fill the space between two code blocks with comment.
   * <p>
   * Another strategy: create a lexer that can tokenize some pieces of source code
   */
  public static @NotNull String extractAya(@NotNull Literate literate) {
    var codeBlocks = new LiterateConsumer.AyaCodeBlocks(MutableList.create()).extract(literate);
    var builder = new StringBuilder();
    var index = -1;  // current index (the index of the last character)
    var line = 1;   // current line (1 based)

    for (var block : codeBlocks) {
      // block.isAya = true

      var sourcePos = block.sourcePos;

      // A code block that doesn't matter, skip
      if (sourcePos == null) continue;

      // reach to the line above the code block
      var lineTarget = sourcePos.startLine() - 1;
      while (line < lineTarget) {
        builder.append(LINE_SEPARATOR);
        line++;
        index++;
      }

      // line = lineTarget
      // We want to reach the character that before the tokenStartIndex.
      var indexTarget = sourcePos.tokenStartIndex() - 1;
      // This case is probably impossible, because a code block always begin with at least '```'
      assert indexTarget > index : "BUG!";

      // We are at the line above the code block --- The '```' line
      // reach to indexTarget - 1 (1 is for line separator)
      while (index < indexTarget - 1) {
        index++;
        // It is impossible that we only append one or no '/', because a code block always start with three '`'
        builder.append('/');
      }

      var content = block.raw;
      // index = indexTarget - 1
      builder.append(LINE_SEPARATOR);
      index++;
      line++;
      // index = indexTarget
      // line = sourcePos.startLine
      // The cursor is now before the sourcePos.tokenStartIndex

      assert index + 1 == sourcePos.tokenStartIndex();
      assert line == sourcePos.startLine();

      builder.append(content);

      // update line and index
      index += content.length();
      // the cursor is now after the last character
      line += sourcePos.linesOfCode() - 1;
    }

    return builder.toString();
  }

  private @NotNull ImmutableSeq<Literate> mapChildren(
    @NotNull Node parent,
    @NotNull GenericAyaParser producer
  ) {
    Node next;
    var children = MutableList.<Literate>create();
    for (var node = parent.getFirstChild(); node != null; node = next) {
      if (children.isNotEmpty() && node instanceof Paragraph) {
        children.append(new Literate.Raw(Doc.line()));
      }
      next = node.getNext();
      children.append(mapAST(node, producer));
    }
    return children.toImmutableSeq();
  }

  private @NotNull Literate mapAST(
    @NotNull Node node,
    @NotNull GenericAyaParser producer
  ) {
    return switch (node) {
      case Text text -> new Literate.Raw(Doc.plain(text.getLiteral()));
      case Emphasis emphasis -> new Literate.Many(Style.italic(), mapChildren(emphasis, producer));
      case HardLineBreak $ -> new Literate.Raw(Doc.line());
      case SoftLineBreak $ -> new Literate.Raw(Doc.line());
      case StrongEmphasis emphasis -> new Literate.Many(Style.bold(), mapChildren(emphasis, producer));
      case Paragraph $ -> new Literate.Many(MdStyle.GFM.Paragraph, mapChildren(node, producer));
      case BlockQuote $ -> new Literate.Many(MdStyle.GFM.BlockQuote, mapChildren(node, producer));
      case Heading h -> new Literate.Many(new MdStyle.GFM.Heading(h.getLevel()), mapChildren(node, producer));
      case Link h -> new Literate.Link(h.getDestination(), h.getTitle(), mapChildren(node, producer));
      case Document $ -> {
        var children = mapChildren(node, producer);
        yield children.sizeEquals(1) ? children.first() : new Literate.Many(null, children);
      }
      case FencedCodeBlock codeBlock -> {
        var language = codeBlock.getInfo();
        var raw = codeBlock.getLiteral();
        var spans = codeBlock.getSourceSpans();
        if (spans != null && spans.size() >= 2) {   // always contains '```aya' and '```'
          var inner = ImmutableSeq.from(spans).view().drop(1).dropLast(1).toImmutableSeq();
          // remove the last line break if not empty
          if (!raw.isEmpty()) raw = raw.substring(0, raw.length() - 1);
          yield new Literate.CodeBlock(fromSourceSpans(inner), language, raw);
        }
        throw new InternalException("SourceSpans");
      }
      case Code inlineCode -> {
        var spans = inlineCode.getSourceSpans();
        if (spans != null && spans.size() == 1) {
          var sourceSpan = spans.get(0);
          var lineIndex = linesIndex.get(sourceSpan.getLineIndex());
          var startFrom = lineIndex + sourceSpan.getColumnIndex();
          var sourcePos = fromSourceSpans(file, startFrom, Seq.of(sourceSpan));
          assert sourcePos != null;
          yield CodeOptions.analyze(inlineCode, producer, sourcePos);
        }
        throw new InternalException("SourceSpans");
      }
      default -> {
        var spans = node.getSourceSpans();
        if (spans == null) throw new InternalException("SourceSpans");
        var pos = fromSourceSpans(Seq.from(spans));
        if (pos == null) throw new UnsupportedOperationException("TODO: Which do the nodes have not source spans?");
        producer.reporter().report(new UnsupportedMarkdown(pos, node.getClass().getSimpleName()));
        yield new Literate.Unsupported(mapChildren(node, producer));
      }
    };
  }

  public @Nullable SourcePos fromSourceSpans(@NotNull Seq<SourceSpan> sourceSpans) {
    if (sourceSpans.isEmpty()) return null;
    var startFrom = linesIndex.get(sourceSpans.first().getLineIndex());

    return fromSourceSpans(file, startFrom, sourceSpans);
  }

  /**
   * Build a {@link SourcePos} from a list of {@link SourceSpan}
   *
   * @param startFrom   the SourcePos should start from. (inclusive)
   * @param sourceSpans a not null sequence
   * @return null if an empty sourceSpans
   */
  @Contract(pure = true) public static @Nullable SourcePos
  fromSourceSpans(@NotNull SourceFile file, int startFrom, @NotNull Seq<SourceSpan> sourceSpans) {
    if (sourceSpans.isEmpty()) return null;

    var it = sourceSpans.iterator();
    var beginSpan = it.next();
    var endSpan = beginSpan;
    var endLine = beginSpan.getLineIndex();
    var endColumn = beginSpan.getLength() - 1;
    var totalLength = beginSpan.getLength();

    while (it.hasNext()) {
      var curSpan = it.next();

      // Continuous?
      while (endSpan.getLineIndex() + 1 != curSpan.getLineIndex()) {
        totalLength++;
        endSpan = SourceSpan.of(endSpan.getLineIndex() + 1, -1, 0);
      }

      // Now continuous!
      endLine = curSpan.getLineIndex();
      endColumn = curSpan.getLength() - 1;
      // 1 is for line separator
      totalLength += 1 + curSpan.getLength();

      endSpan = curSpan;
    }

    return new SourcePos(file,
      startFrom, startFrom + totalLength - 1,
      beginSpan.getLineIndex() + 1, beginSpan.getColumnIndex(),
      endLine + 1, endColumn);
  }
}
