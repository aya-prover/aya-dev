// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.parse;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.DefaultPsiParser;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableArrayList;
import org.aya.cli.literate.FlclToken;
import org.aya.intellij.MarkerNodeWrapper;
import org.aya.parser.FlclLanguage;
import org.aya.parser.FlclParserDefinition;
import org.aya.parser.FlclPsiElementTypes;
import org.aya.util.error.SourceFile;
import org.aya.util.reporter.Problem;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;

public record FlclParser(
  @NotNull Reporter reporter, @NotNull SourceFile file,
  @NotNull EnumMap<FlclToken.Type, ImmutableSeq<String>> decls
) {
  public @NotNull FlclToken.File program(@NotNull String file) {
    var node = new MarkerNodeWrapper(file, new FlclFleetParser().parse(file));
    node.childrenOfType(FlclPsiElementTypes.RULE).forEach(rule -> {
      var idChildren = rule.childrenOfType(FlclPsiElementTypes.ID)
        .map(MarkerNodeWrapper::tokenText)
        .map(CharSequence::toString);
      var title = idChildren.first();
      var ids = idChildren.drop(1).toImmutableSeq();
      // Replace with a loop?
      switch (title) {
        case "keyword" -> decls.put(FlclToken.Type.Keyword, ids);
        case "fn" -> decls.put(FlclToken.Type.Fn, ids);
        case "data" -> decls.put(FlclToken.Type.Data, ids);
        case "local" -> decls.put(FlclToken.Type.Local, ids);
        default -> reporter.reportString("Unknown rule: " + title, Problem.Severity.WARN);
      }
    });
    var ids = node.childrenOfType(FlclPsiElementTypes.ID).toImmutableSeq();
    var nums = node.childrenOfType(FlclPsiElementTypes.NUMBER).toImmutableSeq();
    var tokens = MutableArrayList.<FlclToken>create(ids.size() + nums.size());
    ids.mapNotNullTo(tokens, this::computeType);
    nums.mapTo(tokens, n -> computeToken(n.range(), FlclToken.Type.Number));
    int startIndex = node.child(FlclPsiElementTypes.SEPARATOR).range().getEndOffset() + 1;
    return new FlclToken.File(tokens.toImmutableSeq(), startIndex);
  }

  private @Nullable FlclToken computeType(@NotNull MarkerNodeWrapper text) {
    for (var entry : decls.entrySet()) {
      if (entry.getValue().contains(text.tokenText().toString())) {
        return computeToken(text.range(), entry.getKey());
      }
    }
    return null;
  }

  private @NotNull FlclToken computeToken(TextRange range, FlclToken.Type key) {
    return new FlclToken(AyaProducer.sourcePosOf(range, file, true), key);
  }

  private static class FlclFleetParser extends DefaultPsiParser {
    public FlclFleetParser() {
      super(new FlclParserDefinition(ParserUtil.forLanguage(FlclLanguage.INSTANCE)));
    }
  }
}
