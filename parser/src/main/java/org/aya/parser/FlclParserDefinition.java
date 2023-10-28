// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.parser;

import com.intellij.lang.PsiParser;
import com.intellij.lexer.FlexAdapter;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.tree.IFileElementType;
import org.jetbrains.annotations.NotNull;

public class FlclParserDefinition extends ParserDefBase.WithFile {
  public FlclParserDefinition(@NotNull IFileElementType file) {
    super(file);
  }

  @Override public @NotNull Lexer createLexer(Project project) {
    return new FlexAdapter(new _FlclPsiLexer());
  }

  @Override public @NotNull PsiParser createParser(Project project) {
    return new FlclPsiParser();
  }
}
