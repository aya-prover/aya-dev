// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.parse;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.FleetPsiParser;
import com.intellij.psi.TokenType;
import com.intellij.psi.builder.FleetPsiBuilder;
import com.intellij.psi.builder.MarkerNode;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.control.Either;
import org.aya.concrete.Expr;
import org.aya.concrete.GenericAyaParser;
import org.aya.concrete.error.ParseError;
import org.aya.concrete.stmt.Stmt;
import org.aya.parser.AyaLanguage;
import org.aya.parser.AyaParserDefinitionBase;
import org.aya.parser.AyaPsiElementTypes;
import org.aya.parser.GenericNode;
import org.aya.util.error.SourceFile;
import org.aya.util.error.SourcePos;
import org.aya.util.reporter.Problem;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.Objects;

public record AyaParserImpl(@NotNull Reporter reporter) implements GenericAyaParser {
  private static final @NotNull TokenSet ERROR = TokenSet.create(TokenType.ERROR_ELEMENT, TokenType.BAD_CHARACTER);

  public @NotNull GenericNode<?> parseNode(@NotNull String code) {
    var parser = new AyaFleetParser();
    return new NodeWrapper(parser.parse(code));
  }

  @Override public @NotNull Expr expr(@NotNull String code, @NotNull SourcePos sourcePos) {
    var node = parseNode("prim a : " + code);
    var type = node.child(AyaPsiElementTypes.PRIM_DECL).child(AyaPsiElementTypes.TYPE);
    return new AyaProducer(code, Either.right(sourcePos), reporter).type(type);
  }

  @Override public @NotNull ImmutableSeq<Stmt> program(@NotNull SourceFile sourceFile, @NotNull SourceFile errorReport) {
    var parse = parse(sourceFile.sourceCode(), errorReport);
    if (parse.isRight()) {
      reporter.reportString("Expect statement, got repl expression", Problem.Severity.ERROR);
      return ImmutableSeq.empty();
    }
    return parse.getLeftValue();
  }

  private @NotNull Either<ImmutableSeq<Stmt>, Expr> parse(@NotNull String code, @NotNull SourceFile errorReport) {
    var node = reportErrorElements(parseNode(code), errorReport);
    return new AyaProducer(code, Either.left(errorReport), reporter).program(node);
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

  private record NodeWrapper(@NotNull MarkerNode node) implements GenericNode<NodeWrapper> {
    @Override public @NotNull IElementType elementType() {
      return node.elementType();
    }

    @Override public @NotNull String tokenText() {
      if (isTerminalNode()) return Objects.requireNonNull(node.text());
      var sb = new StringBuilder();
      buildText(sb);
      return sb.toString();
    }

    @Override public boolean isTerminalNode() {
      return node.text() != null;
    }

    @Override public @NotNull SeqView<NodeWrapper> childrenView() {
      return node.children().view().map(NodeWrapper::new);
    }

    private void buildText(@NotNull StringBuilder builder) {
      if (node.text() != null) builder.append(node.text());
      else childrenView().forEach(c -> c.buildText(builder));
    }

    @Override public @NotNull TextRange range() {
      return node.range();
    }

    @Override public @NotNull String toDebugString() {
      return node.toDebugString("  ");
    }
  }

  private static class AyaFleetParser extends FleetPsiParser.DefaultPsiParser {
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
