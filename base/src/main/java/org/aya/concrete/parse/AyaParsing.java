// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.parse;

import kala.collection.immutable.ImmutableSeq;
import kala.control.Either;
import kala.control.Option;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CodePointBuffer;
import org.antlr.v4.runtime.CodePointCharStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.aya.api.error.Reporter;
import org.aya.api.error.SourceFile;
import org.aya.api.error.SourceFileLocator;
import org.aya.concrete.Expr;
import org.aya.concrete.stmt.Stmt;
import org.aya.parser.AyaLexer;
import org.aya.parser.AyaParser;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

public interface AyaParsing {
  @Contract("_ -> new") static @NotNull AyaParser parser(@NotNull String text) {
    return new AyaParser(new CommonTokenStream(
      new AyaLexer(CharStreams.fromString(text))));
  }

  @Contract("_, _ -> new") static @NotNull AyaParser parser(@NotNull String text, @NotNull Reporter reporter) {
    return parser(new SourceFile(Option.none(), text), reporter);
  }

  @Contract("_, _ -> new")
  private static @NotNull AyaParser parser(@NotNull SourceFile sourceFile, @NotNull Reporter reporter) {
    var intBuffer = IntBuffer.wrap(sourceFile.sourceCode().codePoints().toArray());
    var codePointBuffer = CodePointBuffer.withInts(intBuffer);
    var charStream = CodePointCharStream.fromBuffer(codePointBuffer);
    var lexer = new AyaLexer(charStream);
    lexer.removeErrorListeners();
    var listener = new ReporterErrorListener(sourceFile, reporter);
    lexer.addErrorListener(listener);
    var parser = new AyaParser(new CommonTokenStream(lexer));
    parser.removeErrorListeners();
    parser.addErrorListener(listener);
    return parser;
  }

  static @NotNull ImmutableSeq<Stmt> program(
    @NotNull SourceFileLocator locator,
    @NotNull Reporter reporter, @NotNull Path path
  ) throws IOException {
    var sourceCode = Files.readString(path);
    var sourceFile = new SourceFile(Option.some(locator.displayName(path)), sourceCode);
    var parser = parser(sourceFile, reporter);
    return new AyaProducer(sourceFile, reporter).visitProgram(parser.program());
  }

  static @Nullable Either<ImmutableSeq<Stmt>, Expr> repl(
    @NotNull Reporter reporter, @NotNull String text
  ) {
    var sourceFile = new SourceFile(Option.none(), text);
    var parser = parser(sourceFile, reporter);
    return new AyaProducer(sourceFile, reporter).visitRepl(parser.repl());
  }
}
