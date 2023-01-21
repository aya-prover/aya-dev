// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.repl.gk;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.TokenType;
import kala.collection.immutable.ImmutableSeq;
import org.aya.repl.ReplLexer;
import org.jetbrains.annotations.NotNull;

public record GKReplLexer(@NotNull FlexLexer lexer) implements ReplLexer<FlexLexer.Token> {
  @Override public void reset(@NotNull CharSequence buf, int start, int end, int initialState) {
    lexer.reset(buf, start, end, initialState);
  }

  @Override public @NotNull ImmutableSeq<FlexLexer.Token> allTheWayDown() {
    return lexer.allTheWayDown();
  }

  @Override public boolean isWhitespace(FlexLexer.@NotNull Token token) {
    return token.type() == TokenType.WHITE_SPACE;
  }

  @Override public int startOffset(FlexLexer.@NotNull Token token) {
    return token.range().getStartOffset();
  }

  @Override public boolean containsOffset(FlexLexer.@NotNull Token token, int offset) {
    return token.range().containsOffset(offset);
  }

  @Override public @NotNull String tokenText(@NotNull String where, FlexLexer.@NotNull Token token) {
    return token.range().substring(where);
  }
}
