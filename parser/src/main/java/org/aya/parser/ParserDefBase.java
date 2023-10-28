// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.parser;

import com.intellij.lang.ASTNode;
import com.intellij.lang.ParserDefinition;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;

public interface ParserDefBase extends ParserDefinition {
  @Override default @NotNull SpaceRequirements spaceExistenceTypeBetweenTokens(ASTNode left, ASTNode right) {
    return SpaceRequirements.MAY;
  }

  @NotNull AyaPsiTokenType LINE_COMMENT = new AyaPsiTokenType("LINE_COMMENT");
  @NotNull AyaPsiTokenType BLOCK_COMMENT = new AyaPsiTokenType("BLOCK_COMMENT");
  @NotNull TokenSet COMMENTS = TokenSet.create(LINE_COMMENT, BLOCK_COMMENT);

  @Override default @NotNull TokenSet getCommentTokens() {
    // Remark needs DOC_COMMENT, do not skip it.
    return ParserDefBase.COMMENTS;
  }
}
