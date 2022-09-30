// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.parse;

import com.intellij.psi.FleetPsiParser;
import com.intellij.psi.builder.ASTMarkerVisitor;
import com.intellij.psi.builder.FleetPsiBuilder;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IFileElementType;
import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.control.Either;
import org.aya.concrete.Expr;
import org.aya.concrete.GenericAyaParser;
import org.aya.concrete.stmt.Stmt;
import org.aya.parser.ij.AyaLanguage;
import org.aya.parser.ij.AyaParserDefinitionBase;
import org.aya.parser.ij.AyaPsiElementTypes;
import org.aya.parser.ij.GenericNode;
import org.aya.util.error.SourceFile;
import org.aya.util.error.SourcePos;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public record AyaGKParserImpl(@NotNull Reporter reporter) implements GenericAyaParser {
  public @NotNull GenericNode<?> tokens(@NotNull String code) {
    var parser = new AyaFleetParser();
    return new NodeWrapper(parser.parse(code));
  }

  @Override public @NotNull Expr expr(@NotNull String code, @NotNull SourcePos sourcePos) {
    var node = tokens("def a : " + code);
    var type = node.child(AyaPsiElementTypes.FN_DECL).child(AyaPsiElementTypes.TYPE);
    return new AyaGKProducer(Either.right(sourcePos), reporter).type(type);
  }

  @Override public @NotNull ImmutableSeq<Stmt> program(@NotNull SourceFile sourceFile) {
    var node = tokens(sourceFile.sourceCode());
    return new AyaGKProducer(Either.left(sourceFile), reporter).program(node);
  }

  private record NodeWrapper(@NotNull ASTMarkerVisitor.Node node) implements GenericNode<NodeWrapper> {
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

    @Override public int startOffset() {
      return node.startOffset();
    }

    @Override public int endOffset() {
      return node.endOffset();
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
