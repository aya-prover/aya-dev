// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.parse;

import com.intellij.psi.FleetPsiParser;
import com.intellij.psi.builder.FleetPsiBuilder;
import com.intellij.psi.tree.IFileElementType;
import kala.collection.immutable.ImmutableSeq;
import kala.control.Either;
import org.aya.concrete.Expr;
import org.aya.concrete.GenericAyaParser;
import org.aya.concrete.stmt.Stmt;
import org.aya.parser.ij.AyaLanguage;
import org.aya.parser.ij.AyaParserDefinitionBase;
import org.aya.parser.ij.AyaPsiElementTypes;
import org.aya.util.error.SourceFile;
import org.aya.util.error.SourcePos;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.NotNull;

public record AyaGKParserImpl(@NotNull Reporter reporter) implements GenericAyaParser {
  @Override public @NotNull Expr expr(@NotNull String code, @NotNull SourcePos sourcePos) {
    var parser = new AyaFleetParser();
    var node = parser.parse(AyaPsiElementTypes.EXPR, code);
    return new AyaGKProducer(Either.right(sourcePos), reporter).expr(node);
  }

  @Override public @NotNull ImmutableSeq<Stmt> program(@NotNull SourceFile sourceFile) {
    var parser = new AyaFleetParser();
    var node = parser.parse(sourceFile.sourceCode());
    return new AyaGKProducer(Either.left(sourceFile), reporter).program(node);
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
