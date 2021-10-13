// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.parse;

import kala.collection.immutable.ImmutableSeq;
import kala.control.Either;
import kala.control.Option;
import org.antlr.v4.runtime.*;
import org.aya.api.error.DelayedReporter;
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
import java.util.function.BiFunction;
import java.util.function.Function;

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
    var parser = AyaParsing.parser(sourceFile, reporter);
    return new AyaProducer(sourceFile, reporter).visitProgram(parser.program());
  }

  static <Context extends ParserRuleContext, AyaStruct /*extends AyaDocile*/>
  @Nullable AyaStruct tryAyaStruct(
    @NotNull Reporter reporter, @NotNull String text,
    @NotNull Function<AyaParser, Context> getContext,
    @NotNull BiFunction<AyaProducer, Context, AyaStruct> visitAyaStruct
  ) {
    var sourceFile = new SourceFile(Option.none(), text);
    var parser = AyaParsing.parser(sourceFile, reporter);
    try {
      var context = getContext.apply(parser);
      return visitAyaStruct.apply(new AyaProducer(sourceFile, reporter), context);
    } catch (ParsingInterruptedException e) {
      return null;
    }
  }

  static @Nullable ImmutableSeq<Stmt> tryProgram(
    @NotNull Reporter reporter, @NotNull String text
  ) {
    return tryAyaStruct(reporter, text, AyaParser::program, AyaProducer::visitProgram);
  }

  static @Nullable Expr tryExpr(
    @NotNull Reporter reporter, @NotNull String text
  ) {
    return tryAyaStruct(reporter, text, AyaParser::expr, AyaProducer::visitExpr);
  }

  static @Nullable Either<ImmutableSeq<Stmt>, Expr> tryProgramOrExpr(
    @NotNull Reporter reporter, @NotNull String text
  ) {
    var delayedReporter = new DelayedReporter(reporter);

    var program = AyaParsing.tryProgram(delayedReporter, text);
    if (program != null)
      return Either.left(program);
    else {
      var expr = AyaParsing.tryExpr(delayedReporter, text);
      if (expr != null)
        return Either.right(expr);
      else {
        delayedReporter.reportNow();
        return null;
      }
    }
  }
}
