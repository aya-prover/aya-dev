// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.repl.gk;

import com.intellij.KalaTODO;
import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;
import kala.collection.Seq;
import kala.function.CheckedSupplier;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Objects;
import java.util.stream.Stream;

public record JFlexAdapter(@NotNull FlexLexer lexer) {
  // TODO: https://github.com/Glavo/kala-common/issues/61

  /** @return parsed tokens without the last EOF token */
  @KalaTODO @NotNull Seq<Token> tokensNoEOF(String line) {
    lexer.reset(line, 0, line.length(), 0);
    CheckedSupplier<IElementType, IOException> action = lexer::advance;
    return Seq.wrapJava(Stream.generate(() -> {
      var type = action.get();
      if (type == null) return null;
      return new Token(lexer.getTokenStart(), lexer.getTokenEnd(), type,
        line.substring(lexer.getTokenStart(), lexer.getTokenEnd()));
    }).takeWhile(Objects::nonNull).toList());
  }

  public record Token(int tokenStart, int tokenEnd, @NotNull IElementType type, @NotNull String text) {
  }
}
