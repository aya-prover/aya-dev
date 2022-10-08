// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.repl.gk;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;
import kala.collection.Seq;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

public record JFlexAdapter(@NotNull FlexLexer lexer) {
  /** @return parsed tokens without the last EOF token */
  @NotNull Seq<Token> tokensNoEOF(String line) {
    lexer.reset(line, 0, line.length(), 0);
    Supplier<IElementType> action = lexer::advanceUnchecked;
    return Seq.generateUntilNull(() -> {
      var type = action.get();
      if (type == null) return null;
      return new Token(lexer.getTokenStart(), lexer.getTokenEnd(), type,
        line.substring(lexer.getTokenStart(), lexer.getTokenEnd()));
    });
  }

  public record Token(int tokenStart, int tokenEnd, @NotNull IElementType type, @NotNull String text) {
  }
}
