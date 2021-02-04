// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.concrete.parse;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.mzi.api.error.Reporter;
import org.mzi.parser.MziLexer;
import org.mzi.parser.MziParser;

import java.io.IOException;
import java.nio.file.Path;

public interface MziParsing {
  @Contract("_ -> new") static @NotNull MziParser parser(@NotNull String text) {
    return new MziParser(new CommonTokenStream(
      new MziLexer(CharStreams.fromString(text))));
  }

  @Contract("_, _ -> new")
  static @NotNull MziParser parser(@NotNull Path path, @NotNull Reporter reporter) throws IOException {
    var lexer = new MziLexer(CharStreams.fromPath(path));
    lexer.removeErrorListeners();
    var listener = new ReporterErrorListener(reporter);
    lexer.addErrorListener(listener);
    var parser = new MziParser(new CommonTokenStream(lexer));
    parser.removeErrorListeners();
    parser.addErrorListener(listener);
    return parser;
  }
}
