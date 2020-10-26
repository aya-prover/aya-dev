// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.concrete.parse;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.mzi.parser.LispLexer;

/**
 * @author ice1000
 */
public interface LispParsing {
  @Contract("_ -> new") static @NotNull org.mzi.parser.LispParser parser(@NotNull String text) {
    return new org.mzi.parser.LispParser(new CommonTokenStream(lexer(text)));
  }

  @Contract("_ -> new") private static @NotNull LispLexer lexer(@NotNull String text) {
    return new LispLexer(CharStreams.fromString(text));
  }
}
