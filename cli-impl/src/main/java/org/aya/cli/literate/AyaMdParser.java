// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.literate;

import kala.collection.Seq;
import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import kala.tuple.Tuple;
import kala.tuple.Tuple2;
import kala.tuple.primitive.IntObjTuple2;
import kala.value.LazyValue;
import kala.value.MutableValue;
import org.aya.concrete.remark.Literate;
import org.aya.concrete.remark.LiterateConsumer;
import org.aya.concrete.remark.UnsupportedMarkdown;
import org.aya.concrete.remark.code.CodeAttrProcessor;
import org.aya.concrete.remark.code.CodeOptions;
import org.aya.concrete.remark.math.InlineMath;
import org.aya.concrete.remark.math.MathBlock;
import org.aya.generic.util.InternalException;
import org.aya.pretty.backend.md.MdStyle;
import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Style;
import org.aya.util.StringUtil;
import org.aya.util.error.SourceFile;
import org.aya.util.error.SourcePos;
import org.aya.util.reporter.Reporter;
import org.commonmark.node.*;
import org.commonmark.parser.IncludeSourceSpans;
import org.commonmark.parser.Parser;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AyaMdParser {

  /** For empty line that end with \n, the index points to \n */
  private final @NotNull ImmutableSeq<Integer> linesIndex;
  private final @NotNull SourceFile file;
  private final @NotNull Reporter reporter;

  public AyaMdParser(@NotNull SourceFile file, @NotNull Reporter reporter) {
    this.file = file;
    this.reporter = reporter;
    this.linesIndex = StringUtil.indexedLines(file.sourceCode()).map(IntObjTuple2::component1);
  }

  public @NotNull Literate parseLiterate() {
    var parser = Parser.builder()
      .customDelimiterProcessor(CodeAttrProcessor.INSTANCE)
      .customDelimiterProcessor(InlineMath.Processor.INSTANCE)
      .customBlockParserFactory(MathBlock.Factory.INSTANCE)
      .includeSourceSpans(IncludeSourceSpans.BLOCKS_AND_INLINES)
      .postProcessor(FillCodeBlock.INSTANCE)
      .build();
    return mapAST(parser.parse(file.sourceCode()));
  }

  /**
   * Extract all aya code blocks, keep source poses.
   * Fill the space between two code blocks with comment.
   * <p>
   * Another strategy: create a lexer that can tokenize some pieces of source code
   */
  public @NotNull String extractAya(@NotNull Literate literate) {
    var codeBlocks = new LiterateConsumer.AyaCodeBlocks(MutableList.create())
      .extract(literate)
      .view()
      .filter(x -> x.sourcePos != null);

    var builder = new StringBuilder(file.sourceCode().length());
    @Nullable Literate.CodeBlock head = codeBlocks.firstOrNull();
    codeBlocks = codeBlocks.drop(1);

    for (var idx = 0; idx < file.sourceCode().length(); ++idx) {
      var theChar = file.sourceCode().charAt(idx);

      if (head != null) {
        assert head.sourcePos != null : "Physical doesn't exist!!";
        if (head.sourcePos.tokenEndIndex() < idx) {
          assert idx - head.sourcePos.tokenEndIndex() == 1;
          head = codeBlocks.firstOrNull();
          codeBlocks = codeBlocks.drop(1);
        }

        assert head == null || head.sourcePos != null : "Physical doesn't exist!!";
        if (head != null && head.sourcePos.contains(idx)) {
          builder.append(theChar);
          continue;
        }
      }

      builder.append(theChar == '\n' ? '\n' : ' ');
    }

    return builder.toString();
  }

  private @NotNull ImmutableSeq<Literate> mapChildren(@NotNull Node parent) {
    Node next;
    var children = MutableList.<Literate>create();
    for (var node = parent.getFirstChild(); node != null; node = next) {
      if (children.isNotEmpty() && node instanceof Paragraph) {
        children.append(new Literate.Raw(Doc.line()));
      }
      next = node.getNext();
      children.append(mapAST(node));
    }
    return children.toImmutableSeq();
  }

  private @NotNull Tuple2<LazyValue<SourcePos>, String> stripTrailingNewline(@NotNull String literal, @NotNull Block owner) {
    var spans = owner.getSourceSpans();
    if (spans != null && spans.size() >= 2) {   // always contains '```aya' and '```'
      var inner = ImmutableSeq.from(spans).view().drop(1).dropLast(1).toImmutableSeq();
      // remove the last line break if not empty
      if (!literal.isEmpty())
        literal = literal.substring(0, literal.length() - 1);
      return Tuple.of(LazyValue.of(() -> fromSourceSpans(inner)), literal);
    }
    throw new InternalException("SourceSpans");
  }

  private @NotNull Literate mapAST(@NotNull Node node) {
    return switch (node) {
      case Text text -> new Literate.Raw(Doc.plain(text.getLiteral()));
      case Emphasis emphasis -> new Literate.Many(Style.italic(), mapChildren(emphasis));
      case HardLineBreak $ -> new Literate.Raw(Doc.line());
      case SoftLineBreak $ -> new Literate.Raw(Doc.line());
      case StrongEmphasis emphasis -> new Literate.Many(Style.bold(), mapChildren(emphasis));
      case Paragraph p -> new Literate.Many(MdStyle.GFM.Paragraph, mapChildren(p));
      case BlockQuote b -> new Literate.Many(MdStyle.GFM.BlockQuote, mapChildren(b));
      case Heading h -> new Literate.Many(new MdStyle.GFM.Heading(h.getLevel()), mapChildren(h));
      case Link h -> new Literate.HyperLink(h.getDestination(), h.getTitle(), mapChildren(h));
      case Image h -> new Literate.Image(h.getDestination(), mapChildren(h));
      case ListItem item -> flatten(collectChildren(item.getFirstChild())
        // .flatMap(p -> p instanceof Paragraph ? collectChildren(p.getFirstChild()) : SeqView.of(p))
        .flatMap(this::mapChildren)
        .toImmutableSeq());
      case OrderedList ordered -> new Literate.List(mapChildren(ordered), true);
      case BulletList bullet -> new Literate.List(mapChildren(bullet), false);
      case Document d -> flatten(mapChildren(d));
      case HtmlBlock html when html.getLiteral().startsWith("<!--") -> new Literate.Raw(Doc.empty());
      case ThematicBreak t -> new Literate.Many(MdStyle.GFM.ThematicBreak, mapChildren(t));
      case InlineMath math -> new Literate.Math(true, mapChildren(math));
      case MathBlock math -> {
        var formula = stripTrailingNewline(math.literal, math).component2();
        yield new Literate.Math(false, ImmutableSeq.of(new Literate.Raw(Doc.plain(formula))));
      }
      case FencedCodeBlock codeBlock -> {
        var language = codeBlock.getInfo();
        var code = stripTrailingNewline(codeBlock.getLiteral(), codeBlock);
        yield new Literate.CodeBlock(code.component1().get(), language, code.component2());
      }
      case Code inlineCode -> {
        var spans = inlineCode.getSourceSpans();
        if (spans != null && spans.size() == 1) {
          var sourceSpan = spans.get(0);
          var lineIndex = linesIndex.get(sourceSpan.getLineIndex());
          var startFrom = lineIndex + sourceSpan.getColumnIndex();
          var sourcePos = fromSourceSpans(file, startFrom, Seq.of(sourceSpan));
          assert sourcePos != null;
          yield CodeOptions.analyze(inlineCode, sourcePos);
        }
        throw new InternalException("SourceSpans");
      }
      default -> {
        var spans = node.getSourceSpans();
        if (spans == null) throw new InternalException("SourceSpans");
        var pos = fromSourceSpans(Seq.from(spans));
        if (pos == null) throw new UnsupportedOperationException("TODO: Which do the nodes have not source spans?");
        reporter.report(new UnsupportedMarkdown(pos, node.getClass().getSimpleName()));
        yield new Literate.Unsupported(mapChildren(node));
      }
    };
  }

  private Literate flatten(@NotNull Seq<Literate> children) {
    return children.sizeEquals(1) ? children.first()
      : new Literate.Many(null, children.toImmutableSeq());
  }

  private static @NotNull SeqView<Node> collectChildren(@NotNull Node firstChild) {
    var itemStore = MutableValue.create(firstChild);
    return Seq.generateUntilNull(() -> itemStore.updateAndGet(Node::getNext))
      .view().prepended(firstChild);
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
