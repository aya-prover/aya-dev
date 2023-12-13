// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.literate.parser;

import kala.collection.Seq;
import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.immutable.primitive.ImmutableIntSeq;
import kala.collection.mutable.MutableList;
import kala.tuple.Tuple;
import kala.tuple.Tuple2;
import kala.tuple.primitive.IntObjTuple2;
import kala.value.LazyValue;
import kala.value.MutableValue;
import org.aya.literate.Literate;
import org.aya.literate.UnsupportedMarkdown;
import org.aya.pretty.backend.md.MdStyle;
import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Style;
import org.aya.util.error.InternalException;
import org.aya.util.error.SourceFile;
import org.aya.util.error.SourcePos;
import org.aya.util.more.StringUtil;
import org.aya.util.reporter.Reporter;
import org.commonmark.node.*;
import org.commonmark.parser.IncludeSourceSpans;
import org.commonmark.parser.Parser;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BaseMdParser {
  /** For empty line that end with \n, the index points to \n */
  protected final @NotNull ImmutableIntSeq linesIndex;
  protected final @NotNull SourceFile file;
  protected final @NotNull Reporter reporter;
  protected final @NotNull ImmutableSeq<InterestingLanguage<?>> languages;

  public BaseMdParser(@NotNull SourceFile file, @NotNull Reporter reporter, @NotNull ImmutableSeq<InterestingLanguage<?>> lang) {
    this.linesIndex = StringUtil.indexedLines(file.sourceCode())
      .mapToInt(ImmutableIntSeq.factory(), IntObjTuple2::component1);
    this.file = file;
    this.reporter = reporter;
    this.languages = lang;
  }

  /// region Entry

  protected @NotNull Parser.Builder parserBuilder() {
    return Parser.builder()
      .includeSourceSpans(IncludeSourceSpans.BLOCKS_AND_INLINES)
      .postProcessor(FillCodeBlock.INSTANCE);
  }

  public @NotNull Literate parseLiterate() {
    return mapNode(parserBuilder().build().parse(file.sourceCode()));
  }

  /// endregion Entry

  /// region Parsing

  protected @NotNull ImmutableSeq<Literate> mapChildren(@NotNull Node parent) {
    Node next;
    var children = MutableList.<Literate>create();
    for (var node = parent.getFirstChild(); node != null; node = next) {
      if (children.isNotEmpty() && node instanceof Paragraph) {
        children.append(new Literate.Raw(Doc.line()));
      }
      next = node.getNext();
      children.append(mapNode(node));
    }
    return children.toImmutableSeq();
  }

  protected @NotNull Tuple2<LazyValue<SourcePos>, String> stripTrailingNewline(@NotNull String literal, @NotNull Block owner) {
    var spans = owner.getSourceSpans();
    if (spans != null && spans.size() >= 2) {   // always contains '```' and '```'
      var inner = ImmutableSeq.from(spans).view().drop(1).dropLast(1).toImmutableSeq();
      // remove the last line break if not empty
      if (!literal.isEmpty())
        literal = literal.substring(0, literal.length() - 1);
      return Tuple.of(LazyValue.of(() -> fromSourceSpans(inner)), literal);
    }
    throw new InternalException("SourceSpans");
  }

  protected @NotNull Literate mapNode(@NotNull Node node) {
    return switch (node) {
      case Text text -> new Literate.Raw(Doc.plain(text.getLiteral()));
      case Emphasis emphasis -> new Literate.Many(Style.italic(), mapChildren(emphasis));
      case HardLineBreak _ -> new Literate.Raw(Doc.line());
      case SoftLineBreak _ -> new Literate.Raw(Doc.line());
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
      case FencedCodeBlock codeBlock -> {
        var language = codeBlock.getInfo();
        var code = stripTrailingNewline(codeBlock.getLiteral(), codeBlock);
        yield languages.find(p -> p.test(language))
          .map(factory -> (Literate) factory.create(language, code.component2(), code.component1().get()))
          .getOrElse(() -> {
            var fence = String.valueOf(codeBlock.getFenceChar()).repeat(codeBlock.getFenceLength());
            var raw = Doc.nest(codeBlock.getFenceIndent(), Doc.vcat(
              Doc.escaped(fence + language),
              Doc.escaped(code.component2()),
              Doc.escaped(fence),
              Doc.empty()
            ));
            return new Literate.Raw(raw);
          });
      }
      case Code inlineCode -> {
        var spans = inlineCode.getSourceSpans();
        if (spans != null && spans.size() == 1) {
          var sourceSpan = spans.getFirst();
          var lineIndex = linesIndex.get(sourceSpan.getLineIndex());
          var startFrom = lineIndex + sourceSpan.getColumnIndex();
          var sourcePos = fromSourceSpans(file, startFrom, Seq.of(sourceSpan));
          assert sourcePos != null;
          // FIXME[hoshino]: The sourcePos here contains the beginning and trailing '`'
          yield new Literate.InlineCode(inlineCode.getLiteral(), sourcePos);
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

  protected Literate flatten(@NotNull Seq<Literate> children) {
    return children.sizeEquals(1) ? children.getFirst()
      : new Literate.Many(null, children.toImmutableSeq());
  }

  protected static @NotNull SeqView<Node> collectChildren(@NotNull Node firstChild) {
    var itemStore = MutableValue.create(firstChild);
    return Seq.generateUntilNull(() -> itemStore.updateAndGet(Node::getNext))
      .view().prepended(firstChild);
  }

  /// endregion Parsing

  /// region Helper


  /**
   * Replacing non-code content with whitespaces, keep the source pos of code parts.
   */
  public @NotNull String etching(@NotNull SeqView<Literate.CodeBlock> codeBlocks) {
    codeBlocks = codeBlocks.filter(x -> x.sourcePos != null);

    var builder = new StringBuilder(file.sourceCode().length());
    @Nullable Literate.CodeBlock next = codeBlocks.getFirstOrNull();
    codeBlocks = codeBlocks.drop(1);

    for (var idx = 0; idx < file.sourceCode().length(); ++idx) {
      var theChar = file.sourceCode().charAt(idx);

      if (next != null) {
        assert next.sourcePos != null : "Physical doesn't exist!!";
        if (next.sourcePos.tokenEndIndex() < idx) {
          assert idx - next.sourcePos.tokenEndIndex() == 1;
          next = codeBlocks.getFirstOrNull();
          codeBlocks = codeBlocks.drop(1);
        }

        assert next == null || next.sourcePos != null : "Physical doesn't exist!!";
        if (next != null && next.sourcePos.contains(idx)) {
          builder.append(theChar);
          continue;
        }
      }

      builder.append(theChar == '\n' ? '\n' : ' ');
    }

    return builder.toString();
  }

  public @Nullable SourcePos fromSourceSpans(@NotNull Seq<SourceSpan> sourceSpans) {
    if (sourceSpans.isEmpty()) return null;
    var startFrom = linesIndex.get(sourceSpans.getFirst().getLineIndex());

    return fromSourceSpans(file, startFrom, sourceSpans);
  }

  /**
   * Build a {@link SourcePos} from a list of {@link SourceSpan}
   *
   * @param startFrom   the SourcePos should start from. (inclusive)
   * @param sourceSpans a not null, continuous source span sequence
   * @return null if an empty sourceSpans
   * @see FillCodeBlock
   */
  @Contract(pure = true) public static @Nullable SourcePos
  fromSourceSpans(@NotNull SourceFile file, int startFrom, @NotNull Seq<SourceSpan> sourceSpans) {
    if (sourceSpans.isEmpty()) return null;

    var it = sourceSpans.iterator();
    var beginSpan = it.next();
    var endLine = beginSpan.getLineIndex();
    var endColumn = beginSpan.getLength() - 1;
    var totalLength = beginSpan.getLength();

    // TODO: SeqView?
    while (it.hasNext()) {
      var curSpan = it.next();

      endLine = curSpan.getLineIndex();
      endColumn = curSpan.getColumnIndex();
      // 1 is for line separator
      totalLength += 1 + curSpan.getLength();
    }

    return new SourcePos(file,
      startFrom, startFrom + totalLength - 1,
      beginSpan.getLineIndex() + 1, beginSpan.getColumnIndex(),
      endLine + 1, endColumn);
  }

  /// endregion Helper
}
