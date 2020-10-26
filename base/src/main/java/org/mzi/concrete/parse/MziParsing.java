// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.concrete.parse;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.mzi.parser.MziLexer;
import org.mzi.parser.MziParser;

public interface MziParsing {
  @Contract("_ -> new") static @NotNull MziParser parser(@NotNull String text) {
    return new MziParser(new CommonTokenStream(
      new MziLexer(CharStreams.fromString(text))));
  }
}
