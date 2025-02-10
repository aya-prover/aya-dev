// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.literate;

import com.intellij.openapi.util.text.StringUtil;
import org.aya.literate.Literate;
import org.aya.literate.LiterateConsumer;
import org.aya.literate.parser.BaseMdParser;
import org.aya.prettier.AyaPrettierOptions;
import org.aya.syntax.literate.AyaBacktickParser;
import org.aya.syntax.literate.AyaLiterate;
import org.aya.syntax.literate.CodeOptions;
import org.aya.util.position.SourceFile;
import org.aya.util.reporter.Reporter;
import org.intellij.markdown.ast.ASTNode;
import org.intellij.markdown.parser.MarkerProcessor;
import org.intellij.markdown.parser.markerblocks.MarkerBlockProvider;
import org.intellij.markdown.parser.sequentialparsers.impl.BacktickParser;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Objects;
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
      var code = new StripSurrounding(node.getChildren().getFirst(), 1, 1);
      assert attrSet.getType() == AyaBacktickParser.ATTR_SET;
      var attr = parseAttrSet(attrSet);
      return new AyaLiterate.AyaInlineCode(code.literal(), Objects.requireNonNull(code.sourcePos()), attr);
    }

    return super.mapNode(node);
  }

  public @NotNull CodeOptions parseAttrSet(ASTNode attrSet) {
    var dist = new AyaPrettierOptions();
    var mode = CodeOptions.NormalizeMode.NULL;
    var show = CodeOptions.ShowCode.Core;
    for (var attr : attrSet.getChildren()) {
      if (attr.getType() != AyaBacktickParser.ATTR) continue;
      var key = getTextInNode(attr.getChildren().getFirst());
      // It should be key, colon, double_quote, value, double_quote
      var value = getTextInNode(attr.getChildren().get(attr.getChildren().size() - 2));
      if ("mode".equalsIgnoreCase(key)) {
        mode = cbt(attr, value, CodeOptions.NormalizeMode.values(), CodeOptions.NormalizeMode.NULL);
        continue;
      }
      if ("show".equalsIgnoreCase(key)) {
        show = cbt(attr, value, CodeOptions.ShowCode.values(), CodeOptions.ShowCode.Core);
        continue;
      }
      var cbt = cbt(attr, key, AyaPrettierOptions.Key.values(), null);
      if (cbt != null) {
        var isTrue = "true".equalsIgnoreCase(value) || "yes".equalsIgnoreCase(value);
        dist.map.put(cbt, isTrue);
        continue;
      }
      reporter.report(new AttrWarn.UnknownKey(fromNode(attr), key));
    }
    return new CodeOptions(mode, dist, show);
  }

  private <E extends Enum<E>> E cbt(@NotNull ASTNode attr, @NotNull String userVal, E[] values, E otherwise) {
    for (var val : values)
      if (StringUtil.containsIgnoreCase(val.name(), userVal)) return val;
    reporter.report(new AttrWarn.UnknownValue(fromNode(attr), userVal, values));
    return otherwise;
  }
}
