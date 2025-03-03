// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.producer.flcl;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.DefaultPsiParser;
import com.intellij.psi.TokenType;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableArrayList;
import org.aya.intellij.MarkerNodeWrapper;
import org.aya.parser.FlclLanguage;
import org.aya.parser.FlclParserDefinition;
import org.aya.parser.FlclPsiElementTypes;
import org.aya.producer.ParserUtil;
import org.aya.util.position.SourceFile;
import org.aya.util.position.SourcePos;
import org.aya.util.reporter.Problem;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;

public record FlclParser(
  @NotNull Reporter reporter, @NotNull SourceFile file,
  @NotNull EnumMap<FlclToken.Type, ImmutableSeq<String>> decls
) {
  public FlclParser(@NotNull Reporter reporter, @NotNull SourceFile file) {
    this(reporter, file, new EnumMap<>(FlclToken.Type.class));
  }

  public @NotNull FlclToken.File computeAst() {
    var text = file.sourceCode();
    var node = new MarkerNodeWrapper(new FlclFleetParser().parse(text), text);
    node.childrenOfType(FlclPsiElementTypes.RULE).forEach(rule -> {
      var idChildren = rule.childrenOfType(FlclPsiElementTypes.ID)
        .map(MarkerNodeWrapper::tokenText)
        .map(CharSequence::toString);
      var title = idChildren.getFirst();
      var ids = idChildren.drop(1).toSeq();
      insert(title, ids);
    });
    var body = node.child(FlclPsiElementTypes.BODY);
    var ids = body.childrenOfType(FlclPsiElementTypes.ID).toSeq();
    var nums = body.childrenOfType(FlclPsiElementTypes.NUMBER).toSeq();
    var ws = body.childrenOfType(TokenType.WHITE_SPACE).toSeq();
    var tokens = MutableArrayList.<FlclToken>create(ids.size() + nums.size() + ws.size());
    ids.mapNotNullTo(tokens, this::computeType);
    nums.mapTo(tokens, n -> computeToken(n.range(), FlclToken.Type.Number));
    ws.mapTo(tokens, n -> computeToken(n.range(),
      n.tokenText().indexOf('\n') > 0 ? FlclToken.Type.Eol : FlclToken.Type.WhiteSpace));
    int startIndex = node.child(FlclPsiElementTypes.SEPARATOR).range().getEndOffset() + 1;
    return new FlclToken.File(tokens.toSeq(), body.tokenText(), startIndex);
  }

  private void insert(String title, @NotNull ImmutableSeq<String> ids) {
    for (var tokenType : FlclToken.Type.values()) {
      if (tokenType.name().equalsIgnoreCase(title)) {
        decls.put(tokenType, ids);
        return;
      }
    }
    reporter.reportString("Unknown rule: " + title, Problem.Severity.WARN);
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
    return new FlclToken(SourcePos.of(range, file, true), key);
  }

  private static class FlclFleetParser extends DefaultPsiParser {
    public FlclFleetParser() {
      super(new FlclParserDefinition(ParserUtil.forLanguage(FlclLanguage.INSTANCE)));
    }
  }
}
