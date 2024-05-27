// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.producer;

import com.intellij.lang.Language;
import com.intellij.psi.TokenType;
import com.intellij.psi.builder.FleetPsiBuilder;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
import org.aya.intellij.GenericNode;
import org.aya.producer.error.ParseError;
import org.aya.util.error.SourceFile;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.NotNull;

public interface ParserUtil {
  @NotNull TokenSet ERROR = TokenSet.create(TokenType.ERROR_ELEMENT, TokenType.BAD_CHARACTER);

  static @NotNull GenericNode<?> reportErrorElements(
    @NotNull GenericNode<?> node, @NotNull SourceFile file, @NotNull Reporter reporter
  ) {
    // note: report syntax error here (instead of in Producer) bc
    // IJ plugin directly reports them through PsiErrorElements.
    node.childrenView()
      .filter(i -> ERROR.contains(i.elementType()))
      .forEach(e ->
        reporter.report(new ParseError(AyaProducer.sourcePosOf(e, file),
          "Cannot parse")
        ));
    return node;
  }

  static @NotNull IFileElementType forLanguage(@NotNull Language language) {
    return new IFileElementType(language) {
      @Override public void parse(@NotNull FleetPsiBuilder<?> builder) {
      }
    };
  }
}
