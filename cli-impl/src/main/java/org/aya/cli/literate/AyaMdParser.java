// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.literate;

import org.aya.literate.Literate;
import org.aya.literate.LiterateConsumer;
import org.aya.literate.parser.BaseMdParser;
import org.aya.syntax.literate.AyaBacktickParser;
import org.aya.syntax.literate.AyaLiterate;
import org.aya.syntax.literate.CodeOptions;
import org.aya.util.error.SourceFile;
import org.aya.util.reporter.Reporter;
import org.intellij.markdown.ast.ASTNode;
import org.intellij.markdown.parser.MarkerProcessor;
import org.intellij.markdown.parser.markerblocks.MarkerBlockProvider;
import org.intellij.markdown.parser.sequentialparsers.impl.BacktickParser;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.function.Function;

public class AyaMdParser extends BaseMdParser {
  public AyaMdParser(@NotNull SourceFile file, @NotNull Reporter reporter) {
    super(file, reporter, AyaLiterate.LANGUAGES);
    var index = sequentialParsers.indexWhere(parser -> parser instanceof BacktickParser);
    sequentialParsers.set(index, new AyaBacktickParser());
  }

  @Override protected void addProviders(ArrayList<MarkerBlockProvider<MarkerProcessor.StateInfo>> providers) {
    super.addProviders(providers);
  }

  /**
   * Extract all aya code blocks, keep source poses.
   * Replacing non-code content with whitespaces.
   * <p>
   * Another strategy: create a lexer that can tokenize some pieces of source code
   */
  public @NotNull String extractAya(@NotNull Literate literate) {
    return etching(new LiterateConsumer.InstanceExtractinator<>(AyaLiterate.AyaCodeBlock.class)
      .extract(literate).view()
      .map(Function.identity())
    );
  }

  @Override protected @NotNull Literate mapNode(@NotNull ASTNode node) {
    if (node.getType() == AyaBacktickParser.AYA_CODE_SPAN) {
      var attrSet = node.getChildren().getLast();
      var code = new StripSurrounding(node, 1, 2);
      assert attrSet.getType() == AyaBacktickParser.ATTR_SET;
      var attr = CodeOptions.parseAttrSet(attrSet, this::getTextInNode);
      return new AyaLiterate.AyaInlineCode(code.literal(), code.sourcePos(), attr);
    }

    return super.mapNode(node);
  }

}
