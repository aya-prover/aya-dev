// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.producer;

import com.intellij.psi.DefaultPsiParser;
import kala.collection.immutable.ImmutableSeq;
import kala.control.Either;
import org.aya.intellij.GenericNode;
import org.aya.intellij.MarkerNodeWrapper;
import org.aya.parser.AyaLanguage;
import org.aya.parser.AyaParserDefinitionBase;
import org.aya.parser.AyaPsiElementTypes;
import org.aya.producer.error.ParseError;
import org.aya.syntax.GenericAyaParser;
import org.aya.syntax.GenericAyaProgram;
import org.aya.syntax.concrete.Expr;
import org.aya.syntax.concrete.stmt.Stmt;
import org.aya.util.position.SourceFile;
import org.aya.util.position.SourcePos;
import org.aya.util.position.WithPos;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

public record AyaParserImpl(@NotNull Reporter reporter) implements GenericAyaParser {
  public @NotNull GenericNode<?> parseNode(@NotNull String code) {
    var parser = new AyaFleetParser();
    return new MarkerNodeWrapper(parser.parse(code), code);
  }

  @Override public @NotNull WithPos<Expr> expr(@NotNull String code, @NotNull SourcePos sourcePos) {
    var node = parseNode("prim a : " + code);
    var type = node.child(AyaPsiElementTypes.PRIM_DECL).child(AyaPsiElementTypes.TYPE);
    return new AyaProducer(Either.right(sourcePos), reporter).type(type);
  }

  @Override
  public @NotNull GenericAyaProgram program(@NotNull SourceFile sourceFile, @NotNull SourceFile errorReport) {
    var node = ParserUtil.reportErrorElements(parseNode(sourceFile.sourceCode()), errorReport, reporter);
    var parse = new AyaProducer(Either.left(errorReport), reporter).program(node);
    if (parse.isRight()) {
      reporter.report(new ParseError(parse.getRightValue().sourcePos(),
        "Expect statement, got repl expression"));
      return new NodedAyaProgram(ImmutableSeq.empty(), node);
    }
    return new NodedAyaProgram(parse.getLeftValue(), node);
  }

  private @NotNull Either<ImmutableSeq<Stmt>, WithPos<Expr>> parse(@NotNull String code, @NotNull SourceFile errorReport) {
    var node = ParserUtil.reportErrorElements(parseNode(code), errorReport, reporter);
    return new AyaProducer(Either.left(errorReport), reporter).program(node);
  }

  public @NotNull Either<ImmutableSeq<Stmt>, WithPos<Expr>> repl(@NotNull String code) {
    return parse(code, replSourceFile(code));
  }

  private static @NotNull SourceFile replSourceFile(@NotNull String text) {
    return new SourceFile("<stdin>", Path.of("stdin"), text);
  }

  private static class AyaFleetParser extends DefaultPsiParser {
    public AyaFleetParser() {
      super(new AyaParserDefinitionBase(ParserUtil.forLanguage(AyaLanguage.INSTANCE)));
    }
  }
}
