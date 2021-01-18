// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.concrete.parse;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.mzi.api.error.Reporter;
import org.mzi.parser.MziLexer;
import org.mzi.parser.MziParser;

public interface MziParsing {
  @Contract("_ -> new") static @NotNull MziParser parser(@NotNull String text) {
    return new MziParser(new CommonTokenStream(
      new MziLexer(CharStreams.fromString(text))));
  }

  @Contract("_, _ -> new") static @NotNull MziParser parser(@NotNull String text, @NotNull Reporter reporter) {
    var lexer = new MziLexer(CharStreams.fromString(text));
    lexer.removeErrorListeners();
    var listener = new ReporterErrorListener(reporter);
    lexer.addErrorListener(listener);
    var parser = new MziParser(new CommonTokenStream(lexer));
    parser.removeErrorListeners();
    parser.addErrorListener(listener);
    return parser;
  }
}
