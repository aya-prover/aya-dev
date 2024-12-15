// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.literate;

import com.intellij.psi.tree.TokenSet;
import kala.collection.mutable.MutableList;
import kotlin.ranges.IntRange;
import org.intellij.markdown.MarkdownElementType;
import org.intellij.markdown.MarkdownElementTypes;
import org.intellij.markdown.MarkdownTokenTypes;
import org.intellij.markdown.parser.sequentialparsers.LocalParsingResult;
import org.intellij.markdown.parser.sequentialparsers.RangesListBuilder;
import org.intellij.markdown.parser.sequentialparsers.SequentialParser;
import org.intellij.markdown.parser.sequentialparsers.TokensCache;
import org.intellij.markdown.parser.sequentialparsers.impl.BacktickParser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * <a href="https://github.com/commonmark/commonmark-java/blob/main/commonmark-ext-image-attributes/src/main/java/org/commonmark/ext/image/attributes/internal/ImageAttributesDelimiterProcessor.java">...</a>
 */
public class AyaBacktickParser extends BacktickParser {
  public static final @NotNull MarkdownElementType ATTR = new MarkdownElementType("ATTR", false);
  public static final @NotNull MarkdownElementType ATTR_SET = new MarkdownElementType("ATTR_SET", false);
  public static final @NotNull MarkdownElementType AYA_CODE_SPAN = new MarkdownElementType("AYA_CODE_SPAN", false);

  private static final @NotNull TokenSet BACKTICKS =
    TokenSet.create(MarkdownTokenTypes.BACKTICK, MarkdownTokenTypes.ESCAPED_BACKTICKS);

  private static final @NotNull TokenSet WHITESPACE =
    TokenSet.create(MarkdownTokenTypes.WHITE_SPACE, MarkdownTokenTypes.EOL);

  @Override public @NotNull ParsingResult parse(@NotNull TokensCache tokens, @NotNull List<IntRange> rangesToGlue) {
    var result = new SequentialParser.ParsingResultBuilder();
    var delegateIndices = new RangesListBuilder();
    TokensCache.Iterator iterator = tokens.new RangesListIterator(rangesToGlue);

    while (iterator.getType() != null) {
      if (BACKTICKS.contains(iterator.getType())) {
        var endIterator = findOfSize(iterator.advance(), iterator.getLength());
        if (endIterator != null) {
          iterator = parseAyaCode(iterator, endIterator, result).advance();

          continue;
        }
      }

      delegateIndices.put(iterator.getIndex());
      iterator = iterator.advance();
    }

    return result.withFurtherProcessing(delegateIndices.get());
  }
  private TokensCache.@NotNull Iterator parseAyaCode(TokensCache.Iterator iterator, TokensCache.Iterator endIterator, ParsingResultBuilder result) {
    var codeSpanNode = new Node(new IntRange(iterator.getIndex(), endIterator.getIndex() + 1),
      MarkdownElementTypes.CODE_SPAN);

    // parse aya code attr here
    var attrIt = endIterator.advance();
    if (attrIt.getType() == MarkdownTokenTypes.LPAREN) {
      attrIt = attrIt.advance();

      var wellNodes = MutableList.<Node>create();
      boolean isSuccess = false;

      while (attrIt.getType() != null) {
        if (attrIt.getType() == MarkdownTokenTypes.RPAREN) {
          isSuccess = true;
          break;
        }

        var attr = parseAttr(attrIt);
        if (attr != null) {
          wellNodes.appendAll(attr.getParsedNodes());
          attrIt = attr.getIteratorPosition().advance();
        } else break;
      }

      if (attrIt.getType() == null) {
        isSuccess = false;
      }

      var beginIndex = codeSpanNode.getRange().getStart();
      var attrBeginIndex = codeSpanNode.getRange().getEndInclusive();
      var endIndex = attrIt.getIndex() + 1;

      if (isSuccess) {
        result.withNode(new Node(new IntRange(beginIndex, endIndex), AYA_CODE_SPAN))
          .withNode(codeSpanNode)
          .withNode(new Node(new IntRange(attrBeginIndex, endIndex), ATTR_SET))
          .withNodes(wellNodes.asJava());
        return attrIt;
      } else {
        result.withNode(codeSpanNode);
        return endIterator;
      }
    }
    return iterator;
  }

  private @Nullable LocalParsingResult parseAttr(@NotNull TokensCache.Iterator iterator) {
    while (WHITESPACE.contains(iterator.getType())) iterator = iterator.advance();
    if (iterator.getType() != MarkdownTokenTypes.TEXT) return null;
    var beginIndex = iterator.getIndex();
    iterator = iterator.advance();
    while (WHITESPACE.contains(iterator.getType())) iterator = iterator.advance();
    if (iterator.getType() != MarkdownTokenTypes.COLON) return null;
    iterator = iterator.advance();
    while (WHITESPACE.contains(iterator.getType())) iterator = iterator.advance();
    if (iterator.getType() != MarkdownTokenTypes.TEXT) return null;
    var endIndex = iterator.getIndex();
    return new LocalParsingResult(iterator,
      List.of(new SequentialParser.Node(new IntRange(beginIndex, endIndex + 1), ATTR))
    );
  }
}
