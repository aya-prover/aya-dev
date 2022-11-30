// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.remark2;

import kala.collection.Seq;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import org.aya.concrete.GenericAyaParser;
import org.aya.generic.util.InternalException;
import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Style;
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
  private @NotNull
  final String code;

  /**
   * For empty line that end with EOF, the index points to EOF<br/>
   * For empty line that end with \n, the index points to \n
   */
  private @NotNull
  final ImmutableSeq<Integer> linesIndex;
  private @NotNull
  final SourceFile file;

  public AyaMdParser(@NotNull SourceFile file) {
    this.file = file;
    this.code = file.sourceCode()
      .replace("\r\n", "\n")    // TODO: any elegant way?
      .replace('\r', '\n');

    // build linesIndex
    // The line separator is 1 character wide.
    var linesIndex = MutableList.<Integer>create();
    var lastLineIndex = -1;   // H0 : -1 <=> linesIndex.isEmpty
    var lastLineLength = -1;   // H1 : -1 <=> linesIndex.isEmpty, text length (exclude line separator)

    for (var line : ImmutableSeq.from(code.lines())) {
      var lineIndex = lastLineIndex == -1
        ? 0
        : lastLineIndex + lastLineLength + 1;   // 1 is for line separator
      // lastLineLength is not -1 now

      linesIndex.append(lineIndex);
      lastLineIndex = lineIndex;
      lastLineLength = line.length();
    }

    this.linesIndex = linesIndex.toImmutableSeq();
  }

  private Node parseMd() {
    var parser = Parser.builder()
      .customDelimiterProcessor(CodeAttrProcessor.INSTANCE)
      .includeSourceSpans(IncludeSourceSpans.BLOCKS_AND_INLINES)
      .postProcessor(FillCodeBlock.INSTANCE)
      .build();

    return parser.parse(code);
  }

  public @NotNull Literate parseLiterate(@NotNull GenericAyaParser producer) {
    var node = parseMd();
    return mapAST(node, producer);
  }

  /**
   * Extract all aya code blocks, keep source pos.
   * Fill the space between two code blocks with comment.
   * <p>
   * TODO: Another strategy: create a lexer that can tokenize some pieces of source code
   */
  public static @NotNull String extractAya(@NotNull Literate literate) {
    var codeBlocks = LiterateConsumer.AyaCodeBlocks.codeBlocks(literate);
    var builder = new StringBuilder();
    var index = -1;  // current index (the index of the last character)
    var line = 0;   // current line

    for (var block : codeBlocks) {
      var sourcePos = block.sourcePos;

      // A code block that doesn't matter, skip
      if (sourcePos == null) continue;

      // reach to the line above the code block
      var lineTarget = sourcePos.startLine() - 1;
      while (line < lineTarget) {
        builder.append(LINE_SEPARATOR);
        line = line + 1;
        index = index + 1;
      }

      // line = lineTarget
      // We want to reach the character that before the tokenStartIndex.
      var indexTarget = sourcePos.tokenStartIndex() - 1;
      if (indexTarget <= index) {
        // This case is probably impossible, because a code block always begin with at least '```'
        throw new InternalException("BUG!");
      }

      // We are at the line above the code block --- The '```' line
      // reach to indexTarget - 1 (1 is for line separator)
      var count = 0;
      while (index < indexTarget - 1) {
        index = index + 1;
        // It is impossible that we only append one or no '/', because a code block always start with three '`'
        // TODO: '///' is a Stmt, it would make aya unhappy
        if (count < 2) {
          builder.append('/');
        } else {
          builder.append('\\');
        }

        count = count + 1;
      }

      var content = block.raw;
      // index = indexTarget - 1
      builder.append(LINE_SEPARATOR);
      index = index + 1;
      line = line + 1;
      // index = indexTarget
      // line = sourcePos.startLine
      // The cursor is now before the sourcePos.tokenStartIndex

      assert index + 1 == sourcePos.tokenStartIndex();
      assert line == sourcePos.startLine();

      builder.append(content);

      // update line and index
      index = index + content.length();
      // the cursor is now after the last character
      line = line + sourcePos.linesOfCode() - 1;
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
    if (node instanceof Code code) {
      var sourceSpans = code.getSourceSpans();

      if (sourceSpans != null && sourceSpans.size() == 1) {
        var sourceSpan = sourceSpans.get(0);
        var lineIndex = linesIndex.get(sourceSpan.getLineIndex());
        var startFrom = lineIndex + sourceSpan.getColumnIndex();
        var sourcePos = fromSourceSpans(file, startFrom, Seq.of(sourceSpan));

        assert sourcePos != null;

        return CodeOptions.analyze(code, producer.expr(code.getLiteral(), sourcePos));
      }

      throw new InternalException("Not Enough SourceSpans");
    } else if (node instanceof Text text) {
      return new Literate.Raw(Doc.plain(text.getLiteral()));
    } else if (node instanceof Emphasis emphasis) {
      return new Literate.Many(Style.italic(), mapChildren(emphasis, producer), false);
    } else if (node instanceof HardLineBreak || node instanceof SoftLineBreak) {
      return new Literate.Raw(Doc.line());
    } else if (node instanceof StrongEmphasis emphasis) {
      return new Literate.Many(Style.bold(), mapChildren(emphasis, producer), false);
    } else if (node instanceof Paragraph) {
      return new Literate.Many(null, mapChildren(node, producer), false);
    } else if (node instanceof Document) {
      var children = mapChildren(node, producer);
      if (children.sizeEquals(1)) return children.first();
      else return new Literate.Many(null, children, true);
    } else if (node instanceof FencedCodeBlock codeBlock) {
      var language = codeBlock.getInfo();
      var raw = codeBlock.getLiteral();
      var sourceSpans = codeBlock.getSourceSpans();
      if (sourceSpans != null && sourceSpans.size() >= 2) {   // always contains '```aya' and '```'
        var inner = ImmutableSeq.from(sourceSpans).view().drop(1).dropLast(1).toImmutableSeq();
        // remove the last line break if not empty
        if (!raw.isEmpty()) raw = raw.substring(0, raw.length() - 1);
        return new Literate.CodeBlock(
          fromSourceSpans(inner),
          language, raw);
      }

      throw new InternalException("Not Enough SourceSpans");
    } else {
      var spans = node.getSourceSpans();

      if (spans != null) {
        var pos = fromSourceSpans(Seq.from(spans));

        if (pos == null) {
          throw new UnsupportedOperationException("TODO: Which do the nodes have not source spans?");
        }

        producer.reporter().report(new UnsupportedMarkdown(pos, node.getClass().getSimpleName()));
        return new Literate.Unsupported(mapChildren(node, producer));
      } else {
        throw new InternalException("source spans == null");
      }
    }
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
   * @return null if a empty sourceSpans
   */
  @Contract(pure = true)
  public static @Nullable SourcePos fromSourceSpans(
    @NotNull SourceFile file,
    int startFrom,
    @NotNull Seq<SourceSpan> sourceSpans) {
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
        totalLength = totalLength + 1;
        endSpan = SourceSpan.of(endSpan.getLineIndex() + 1, -1, 0);
      }

      // Now continuous!
      endLine = curSpan.getLineIndex();
      endColumn = curSpan.getLength() - 1;
      totalLength = totalLength + 1 // 1 is for line separator
        + curSpan.getLength();

      endSpan = curSpan;
    }

    return new SourcePos(file,
      startFrom, startFrom + totalLength - 1,
      beginSpan.getLineIndex(), beginSpan.getColumnIndex(),
      endLine, endColumn);
  }
}
