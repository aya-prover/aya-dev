// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.parse;

import com.intellij.psi.DefaultPsiParser;
import com.intellij.psi.TokenType;
import com.intellij.psi.builder.FleetPsiBuilder;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
import kala.collection.immutable.ImmutableSeq;
import kala.control.Either;
import org.aya.concrete.Expr;
import org.aya.concrete.GenericAyaParser;
import org.aya.concrete.error.ParseError;
import org.aya.concrete.stmt.Stmt;
import org.aya.intellij.GenericNode;
import org.aya.intellij.MarkerNodeWrapper;
import org.aya.parser.AyaLanguage;
import org.aya.parser.AyaParserDefinitionBase;
import org.aya.parser.AyaPsiElementTypes;
import org.aya.util.error.SourceFile;
import org.aya.util.error.SourcePos;
import org.aya.util.reporter.Problem;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

public record AyaParserImpl(@NotNull Reporter reporter) implements GenericAyaParser {
  private static final @NotNull TokenSet ERROR = TokenSet.create(TokenType.ERROR_ELEMENT, TokenType.BAD_CHARACTER);

  public @NotNull GenericNode<?> parseNode(@NotNull String code) {
    var parser = new AyaFleetParser();
    return new MarkerNodeWrapper(code, parser.parse(code));
  }

  @Override public @NotNull Expr expr(@NotNull String code, @NotNull SourcePos sourcePos) {
    var node = parseNode("prim a : " + code);
    var type = node.child(AyaPsiElementTypes.PRIM_DECL).child(AyaPsiElementTypes.TYPE);
    return new AyaProducer(Either.right(sourcePos), reporter).type(type);
  }

  @Override
  public @NotNull ImmutableSeq<Stmt> program(@NotNull SourceFile sourceFile, @NotNull SourceFile errorReport) {
    var parse = parse(sourceFile.sourceCode(), errorReport);
    if (parse.isRight()) {
      reporter.reportString("Expect statement, got repl expression", Problem.Severity.ERROR);
      return ImmutableSeq.empty();
    }
    return parse.getLeftValue();
  }

  private @NotNull Either<ImmutableSeq<Stmt>, Expr> parse(@NotNull String code, @NotNull SourceFile errorReport) {
    var node = reportErrorElements(parseNode(code), errorReport);
    return new AyaProducer(Either.left(errorReport), reporter).program(node);
  }

  public @NotNull Either<ImmutableSeq<Stmt>, Expr> repl(@NotNull String code) {
    return parse(code, replSourceFile(code));
  }

  private static @NotNull SourceFile replSourceFile(@NotNull String text) {
    return new SourceFile("<stdin>", Path.of("stdin"), text);
  }

  private @NotNull GenericNode<?> reportErrorElements(@NotNull GenericNode<?> node, @NotNull SourceFile file) {
    // note: report syntax error here (instead of in Producer) bc
    // IJ plugin directly reports them through PsiErrorElements.
    node.childrenView()
      .filter(i -> ERROR.contains(i.elementType()))
      .forEach(e ->
        reporter.report(new ParseError(AyaProducer.sourcePosOf(e, file),
          "Cannot parse")
        ));
    return node;
  }

  private static class AyaFleetParser extends DefaultPsiParser {
    public AyaFleetParser() {
      super(new AyaFleetParserDefinition());
    }

    private static final class AyaFleetParserDefinition extends AyaParserDefinitionBase {
      private final @NotNull IFileElementType FILE = new IFileElementType(AyaLanguage.INSTANCE) {
        @Override public void parse(@NotNull FleetPsiBuilder<?> builder) {
        }
      };

      @Override public @NotNull IFileElementType getFileNodeType() {
        return FILE;
      }
    }
  }
}
