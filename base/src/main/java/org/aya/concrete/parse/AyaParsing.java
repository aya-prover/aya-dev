// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.concrete.parse;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CodePointBuffer;
import org.antlr.v4.runtime.CodePointCharStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.aya.api.error.Reporter;
import org.aya.parser.AyaLexer;
import org.aya.parser.AyaParser;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

public interface AyaParsing {
  @Contract("_ -> new") static @NotNull AyaParser parser(@NotNull String text) {
    return new AyaParser(new CommonTokenStream(
      new AyaLexer(CharStreams.fromString(text))));
  }

  @Contract("_, _ -> new")
  static @NotNull AyaParser parser(@NotNull Path path, @NotNull Reporter reporter) throws IOException {
    var intBuffer = IntBuffer.wrap(Files.readString(path).codePoints().toArray());
    var codePointBuffer = CodePointBuffer.withInts(intBuffer);
    var charStream = CodePointCharStream.fromBuffer(codePointBuffer);
    var lexer = new AyaLexer(charStream);
    lexer.removeErrorListeners();
    var listener = new ReporterErrorListener(reporter);
    lexer.addErrorListener(listener);
    var parser = new AyaParser(new CommonTokenStream(lexer));
    parser.removeErrorListeners();
    parser.addErrorListener(listener);
    return parser;
  }
}
