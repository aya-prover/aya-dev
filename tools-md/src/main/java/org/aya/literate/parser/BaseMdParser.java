// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.literate.parser;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import kala.collection.Seq;
import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import kala.control.Option;
import org.aya.literate.Literate;
import org.aya.literate.UnsupportedMarkdown;
import org.aya.pretty.backend.md.MdStyle;
import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Style;
import org.aya.util.error.SourceFile;
import org.aya.util.error.SourcePos;
import org.aya.util.reporter.Reporter;
import org.intellij.markdown.MarkdownElementTypes;
import org.intellij.markdown.MarkdownTokenTypes;
import org.intellij.markdown.ast.ASTNode;
import org.intellij.markdown.ast.ASTUtilKt;
import org.intellij.markdown.ext.blocks.frontmatter.FrontMatterHeaderProvider;
import org.intellij.markdown.flavours.gfm.*;
import org.intellij.markdown.parser.MarkdownParser;
import org.intellij.markdown.parser.MarkerProcessor;
import org.intellij.markdown.parser.MarkerProcessorFactory;
import org.intellij.markdown.parser.markerblocks.MarkerBlockProvider;
import org.intellij.markdown.parser.sequentialparsers.EmphasisLikeParser;
import org.intellij.markdown.parser.sequentialparsers.SequentialParser;
import org.intellij.markdown.parser.sequentialparsers.SequentialParserManager;
import org.intellij.markdown.parser.sequentialparsers.impl.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

public class BaseMdParser {
  /** For empty line that end with \n, the index points to \n */
  protected final @NotNull SourceFile file;
  protected final @NotNull Reporter reporter;
  protected final @NotNull ImmutableSeq<InterestingLanguage<?>> languages;
  protected final @NotNull MutableList<SequentialParser> sequentialParsers = MutableList.of(
    new AutolinkParser(Seq.of(MarkdownTokenTypes.AUTOLINK, GFMTokenTypes.GFM_AUTOLINK)),
    new BacktickParser(),
    new MathParser(),
    new ImageParser(),
    new InlineLinkParser(),
    new ReferenceLinkParser(),
    new EmphasisLikeParser(new EmphStrongDelimiterParser(), new StrikeThroughDelimiterParser()));

  public BaseMdParser(@NotNull SourceFile file, @NotNull Reporter reporter, @NotNull ImmutableSeq<InterestingLanguage<?>> lang) {
    this.file = file;
    this.reporter = reporter;
    this.languages = lang;
  }

  /// region Entry
  protected void addProviders(ArrayList<MarkerBlockProvider<MarkerProcessor.StateInfo>> providers) {
    providers.addFirst(new FrontMatterHeaderProvider());
  }

  public @NotNull Literate parseLiterate() {
    var flavour = new GFMFlavourDescriptor() {
      @Override public @NotNull SequentialParserManager getSequentialParserManager() {
        return new SequentialParserManager() {
          @Override public @NotNull Seq<SequentialParser> getParserSequence() {
            return sequentialParsers;
          }
        };
      }
      @Override public @NotNull MarkerProcessorFactory getMarkerProcessorFactory() {
        return holder -> new GFMMarkerProcessor(holder, GFMConstraints.Companion.getBASE()) {
          @Override protected @NotNull ArrayList<MarkerBlockProvider<StateInfo>> initMarkerBlockProviders() {
            var providers = super.initMarkerBlockProviders();
            addProviders(providers);
            return providers;
          }
        };
      }
    };
    var parser = new MarkdownParser(flavour);
    return mapNode(parser.buildMarkdownTreeFromString(file.sourceCode()));
  }

  /// endregion Entry

  /// region Parsing

  protected @NotNull ImmutableSeq<Literate> mapChildren(@NotNull ASTNode parent) {
    return mapChildren(parent.getChildren().view());
  }

  public static final TokenSet NATURAL_EOL = TokenSet.create(
    MarkdownElementTypes.PARAGRAPH, MarkdownElementTypes.BLOCK_QUOTE,
    MarkdownElementTypes.CODE_FENCE, MarkdownElementTypes.CODE_BLOCK,
    MarkdownElementTypes.ORDERED_LIST, MarkdownElementTypes.UNORDERED_LIST,
    GFMElementTypes.TABLE, GFMElementTypes.BLOCK_MATH,
    FrontMatterHeaderProvider.FRONT_MATTER_HEADER
  );
  protected @NotNull ImmutableSeq<Literate> mapChildren(@NotNull SeqView<ASTNode> nodes) {
    var children = MutableList.<Literate>create();
    var wantToSkipEol = false;
    for (var child : nodes) {
      if (NATURAL_EOL.contains(child.getType())) wantToSkipEol = true;
      else {
        if (wantToSkipEol && child.getType() == MarkdownTokenTypes.EOL) {
          wantToSkipEol = false;
          continue;
        }
      }

      children.append(mapNode(child));
    }

    return children.toImmutableSeq();
  }

  private static final @NotNull ImmutableSeq<IElementType> HEADINGS = ImmutableSeq.of(
    MarkdownElementTypes.ATX_1,
    MarkdownElementTypes.ATX_2,
    MarkdownElementTypes.ATX_3,
    MarkdownElementTypes.ATX_4,
    MarkdownElementTypes.ATX_5,
    MarkdownElementTypes.ATX_6
  );

  private static Option<ASTNode> peekChild(@NotNull ASTNode node, @NotNull IElementType type) {
    return Option.ofNullable(node.findChildOfType(type));
  }

  @NotNull protected String getTextInNode(@NotNull ASTNode node) {
    return ASTUtilKt.getTextInNode(node, file.sourceCode()).toString();
  }

  private static int isHeading(@NotNull ASTNode node) {
    return HEADINGS.indexOf(node.getType());
  }

  protected record InlineLinkData(@Nullable String title, @NotNull String destination,
                                  @NotNull ImmutableSeq<Literate> children) { }

  protected @NotNull InlineLinkData mapInlineLink(@NotNull ASTNode node) {
    var childNode = node.childOfType(MarkdownElementTypes.LINK_TEXT);
    var destinationNode = node.childOfType(MarkdownElementTypes.LINK_DESTINATION);

    var titleNode = peekChild(node, MarkdownElementTypes.LINK_TITLE);
    var titleTextNode = titleNode.map(x -> x.childOfType(MarkdownTokenTypes.TEXT));

    var destination = getTextInNode(destinationNode);
    var title = titleTextNode.map(this::getTextInNode);
    var children = childNode.childrenWithoutSurrounding(1);

    return new InlineLinkData(title.getOrNull(), destination, mapChildren(children));
  }

  protected @NotNull Literate mapNode(@NotNull ASTNode node) {
    var type = node.getType();

    if (type == MarkdownTokenTypes.TEXT) {
      return new Literate.Raw(Doc.plain(getTextInNode(node)));
    }

    if (type == MarkdownTokenTypes.EOL || type == MarkdownTokenTypes.HARD_LINE_BREAK) {
      return new Literate.Raw(Doc.line());
    }

    // do not confuse with MarkdownTokenTypes.EMPH
    if (type == MarkdownElementTypes.EMPH) {
      return new Literate.Many(Style.italic(), mapChildren(
        node.childrenWithoutSurrounding(1))
      );
    }

    if (type == MarkdownElementTypes.STRONG) {
      return new Literate.Many(Style.italic(), mapChildren(
        node.childrenWithoutSurrounding(2))
      );
    }

    if (type == MarkdownElementTypes.PARAGRAPH) {
      return new Literate.Many(MdStyle.GFM.Paragraph, mapChildren(node));
    }

    if (type == MarkdownElementTypes.BLOCK_QUOTE) {
      return new Literate.Many(MdStyle.GFM.BlockQuote, mapChildren(node));
    }

    var i = isHeading(node);
    if (i != -1) {
      var atxContent = node.childOfType(MarkdownTokenTypes.ATX_CONTENT);
      // 1-based headings
      return new Literate.Many(new MdStyle.GFM.Heading(i + 1),
        mapChildren(atxContent.getChildren().view()
          .dropWhile(it -> it.getType() == MarkdownTokenTypes.WHITE_SPACE)
        )
      );
    }

    if (type == MarkdownElementTypes.INLINE_LINK) {
      var data = mapInlineLink(node);
      return new Literate.HyperLink(data.destination, data.title, data.children);
    }

    if (type == MarkdownElementTypes.IMAGE) {
      var inner = node.childOfType(MarkdownElementTypes.INLINE_LINK);
      var data = mapInlineLink(node);
      return new Literate.Image(data.destination, data.children);
    }

    if (type == MarkdownElementTypes.HTML_BLOCK) {
      var content = getTextInNode(node);
      if (content.startsWith("<!--")) {
        return new Literate.Raw(Doc.empty());
      }
    }

    if (type == MarkdownElementTypes.LIST_ITEM) {
      // not exactly the same as the old one, just hope there is no bugs
      return flatten(mapChildren(node.getChildren().view().drop(1)));
    }

    if (type == MarkdownElementTypes.UNORDERED_LIST) {
      return new Literate.List(mapChildren(node), false);
    }

    if (type == MarkdownElementTypes.ORDERED_LIST) {
      return new Literate.List(mapChildren(node), true);
    }

    if (type == MarkdownTokenTypes.HORIZONTAL_RULE) {
      return new Literate.Many(MdStyle.GFM.ThematicBreak, ImmutableSeq.empty());
    }

    if (type == MarkdownElementTypes.CODE_FENCE) {
      var langInfo = peekChild(node, MarkdownTokenTypes.FENCE_LANG).map(this::getTextInNode);
      var code = new StripSurrounding(node,
        langInfo.isDefined() ? 3 : 2, 2);

      var wellCode = Option.<Literate>none();

      if (langInfo.isDefined()) {
        wellCode = languages.find(p -> p.test(langInfo.get()))
          .map(factory -> {
            return factory.create(code.literal(), code.sourcePos());
          });
      }

      return wellCode.getOrElse(() -> {
        var fence = getTextInNode(node.childOfType(MarkdownTokenTypes.CODE_FENCE_START));
        var raw = Doc.vcat(
          Doc.escaped(fence + langInfo.getOrDefault("")),
          Doc.escaped(code.literal()),
          Doc.escaped(fence),
          Doc.empty()
        );
        return new Literate.Raw(raw);
      });
    }

    if (type == MarkdownElementTypes.CODE_SPAN) {
      var content = new StripSurrounding(node, 1);
      return new Literate.InlineCode(content.literal(), content.sourcePos());
    }

    if (type == GFMElementTypes.INLINE_MATH) {
      var content = new StripSurrounding(node, 1).literal();
      return new Literate.Math(true, ImmutableSeq.of(new Literate.Raw(Doc.plain(content))));
    }

    if (type == GFMElementTypes.BLOCK_MATH) {
      var content = new StripSurrounding(node, 1).literal();
      return new Literate.Math(false, ImmutableSeq.of(new Literate.Raw(Doc.plain(content))));
    }

    if (type == FrontMatterHeaderProvider.FRONT_MATTER_HEADER) {
      return new Literate.Many(null, mapChildren(node));
    }

    if (type == FrontMatterHeaderProvider.FRONT_MATTER_HEADER_DELIMITER) {
      return new Literate.Raw(Doc.plain(getTextInNode(node)));
    }

    if (type == FrontMatterHeaderProvider.FRONT_MATTER_HEADER_CONTENT) {
      return new Literate.Raw(Doc.escaped(getTextInNode(node)));
    }

    if (type == MarkdownElementTypes.MARKDOWN_FILE) {
      return flatten(mapChildren(node));
    }

    var pos = fromNode(node);
    reporter.report(new UnsupportedMarkdown(pos, node.getType().toString()));
    return new Literate.Unsupported(mapChildren(node));
  }

  protected class StripSurrounding {
    private final @NotNull ASTNode first, last;

    public StripSurrounding(@NotNull ASTNode node, int count) {
      this(node, count, count);
    }

    public StripSurrounding(@NotNull ASTNode node, int startCount, int endCount) {
      first = node.getChildren().get(startCount);
      last = node.getChildren().get(node.getChildren().size() - endCount - 1);
    }

    public @NotNull String literal() {
      return file.sourceCode().substring(first.getStartOffset(), last.getEndOffset());
    }

    public @NotNull SourcePos sourcePos() {
      return fromNodes(Seq.of(first, last));
    }
  }

  protected Literate flatten(@NotNull Seq<Literate> children) {
    return children.sizeEquals(1) ? children.getFirst()
      : new Literate.Many(null, children.toImmutableSeq());
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

  public @NotNull SourcePos fromNode(@NotNull ASTNode node) {
    return fromNodes(Seq.of(node));
  }

  /**
   * Build a {@link SourcePos} from a list of {@link ASTNode}
   *
   * @param ranges a non-empty, continuous source span sequence
   */
  public @NotNull SourcePos fromNodes(@NotNull Seq<ASTNode> ranges) {
    assert ranges.isNotEmpty();
    int start = Integer.MAX_VALUE, end = 0;
    for (var node : ranges) {
      start = Math.min(start, node.getStartOffset());
      end = Math.max(end, node.getEndOffset());
    }
    return SourcePos.of(new TextRange(start, end), file, true);
  }
  /// endregion Helper
}
