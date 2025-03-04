// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.literate;

import com.intellij.psi.TokenType;
import com.intellij.psi.tree.TokenSet;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import kala.control.Option;
import org.aya.cli.literate.HighlightInfo.DefKind;
import org.aya.cli.literate.HighlightInfo.Lit;
import org.aya.cli.literate.HighlightInfo.LitKind;
import org.aya.generic.AyaDocile;
import org.aya.parser.AyaParserDefinitionBase;
import org.aya.parser.ParserDefBase;
import org.aya.prettier.BasePrettier;
import org.aya.pretty.doc.Link;
import org.aya.producer.AyaProducer;
import org.aya.syntax.concrete.Expr;
import org.aya.syntax.concrete.Pattern;
import org.aya.syntax.concrete.stmt.ModuleName;
import org.aya.syntax.concrete.stmt.Stmt;
import org.aya.syntax.concrete.stmt.StmtVisitor;
import org.aya.syntax.concrete.stmt.decl.*;
import org.aya.syntax.ref.*;
import org.aya.util.Panic;
import org.aya.util.position.SourceFile;
import org.aya.util.position.SourcePos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record SyntaxHighlight(
  @Nullable ModulePath currentFileModule,
  @NotNull MutableList<HighlightInfo> info
) implements StmtVisitor {
  public static final @NotNull TokenSet SPECIAL_SYMBOL = TokenSet.orSet(
    AyaParserDefinitionBase.UNICODES,
    AyaParserDefinitionBase.MARKERS,
    AyaParserDefinitionBase.DELIMITERS
  );

  /**
   * @param sourceFile If not null, provide keyword highlights too
   * @return a list of {@link HighlightInfo}, no order was expected, the elements may be duplicated
   */
  public static @NotNull ImmutableSeq<HighlightInfo> highlight(
    @Nullable ModulePath currentFileModule,
    @NotNull Option<SourceFile> sourceFile,
    @NotNull ImmutableSeq<Stmt> program
  ) {
    var prettier = new SyntaxHighlight(currentFileModule, MutableList.create());
    program.forEach(prettier);
    if (sourceFile.isDefined()) {
      var addition = lexicalHighlight(sourceFile.get());
      prettier.info.appendAll(addition);
    }
    return prettier.info.toSeq();
  }

  public static @NotNull ImmutableSeq<@NotNull Lit> lexicalHighlight(@NotNull SourceFile file) {
    var lexer = AyaParserDefinitionBase.createLexer(false);
    lexer.reset(file.sourceCode(), 0, file.sourceCode().length(), 0);
    return lexer.allTheWayDown().view()
      .mapNotNull(token -> {
        var tokenType = token.type();
        if (AyaParserDefinitionBase.KEYWORDS.contains(tokenType))
          return new Lit(AyaProducer.sourcePosOf(token, file), LitKind.Keyword);
        else if (ParserDefBase.COMMENTS.contains(tokenType))
          return new Lit(AyaProducer.sourcePosOf(token, file), LitKind.Comment);
        else if (SPECIAL_SYMBOL.contains(tokenType))
          return new Lit(AyaProducer.sourcePosOf(token, file), LitKind.SpecialSymbol);
        if (tokenType == TokenType.WHITE_SPACE) {
          var text = token.range().substring(file.sourceCode());
          return new Lit(AyaProducer.sourcePosOf(token, file), text.contains("\n") ? LitKind.Eol : LitKind.Whitespace);
        }
        return null;
      }).toSeq();
  }

  @Override public void
  visitVarRef(@NotNull SourcePos pos, @NotNull AnyVar var, @NotNull Type type) {
    info.append(linkRef(pos, var, type.toDocile()));
  }

  @Override public void visitExpr(@NotNull SourcePos pos, @NotNull Expr expr) {
    switch (expr) {
      case Expr.LitInt _ -> info.append(LitKind.Int.toLit(pos));
      case Expr.LitString _ -> info.append(LitKind.String.toLit(pos));
      case Expr.Hole hole when hole.filling() == null -> {
        var hover = hole.solution().get();
        if (hover != null) info.append(new HighlightInfo.UserMeta(pos, hover));
      }
      default -> StmtVisitor.super.visitExpr(pos, expr);
    }
  }
  @Override public void visitPattern(@NotNull SourcePos pos, @NotNull Pattern pat) {
    switch (pat) {
      case Pattern.Number _ -> info.append(LitKind.Int.toLit(pos));
      default -> StmtVisitor.super.visitPattern(pos, pat);
    }
  }

  @Override public void visitVarDecl(
    @NotNull SourcePos pos, @NotNull AnyVar var,
    @NotNull Type type
  ) {
    if (var instanceof LocalVar v && v.isGenerated()) return;
    info.append(linkDef(pos, var, type.toDocile()));
  }

  @Override public void visitModuleRef(@NotNull SourcePos pos, @NotNull ModulePath path) {
    // TODO: in SimpleModule.aya line 7, `public open Nat::N` is wrongly linked as `Link.cross`
    //  Because `Stmt.Open` does not provide the fully qualified module name (SimpleModule::Nat::N),
    //  instead, it provides `Nat::N` only, which is not enough to determine whether it is a cross-link or not.
    var link = currentFileModule != null && currentFileModule.sameElements(path)
      ? Link.loc(path.toString())             // referring to a module defined in the current file
      : Link.cross(path.module(), null);  // referring to another file-level module
    info.append(DefKind.Module.toRef(pos, link, null));
  }

  @Override public void visitModuleRef(@NotNull SourcePos pos, @NotNull ModuleName path) {
    info.append(DefKind.Module.toRef(pos, Link.loc(path.toString()), null));
  }

  @Override public void visitModuleDecl(@NotNull SourcePos pos, @NotNull ModuleName path) {
    info.append(DefKind.Module.toDef(pos, Link.loc(path.toString()), null));
  }

  private @NotNull HighlightInfo linkDef(@NotNull SourcePos sourcePos, @NotNull AnyVar var, @Nullable AyaDocile type) {
    return kindOf(var).toDef(sourcePos, BasePrettier.linkIdOf(currentFileModule, var), type);
  }

  private @NotNull HighlightInfo linkRef(@NotNull SourcePos sourcePos, @NotNull AnyVar var, @Nullable AyaDocile type) {
    if (var instanceof LocalVar(_, _, GenerateKind.Generalized(var origin)))
      return linkRef(sourcePos, origin, type);
    return kindOf(var).toRef(sourcePos, BasePrettier.linkIdOf(currentFileModule, var), type);
  }

  private void visitPragma(@NotNull PragmaInfo pInfo) {
    if (pInfo.suppressWarn != null) {
      info.append(new Lit(pInfo.suppressWarn.sourcePos(), LitKind.Keyword));
    }
  }

  @Override
  public void accept(@NotNull Stmt stmt) {
    if (stmt instanceof Decl decl) visitPragma(decl.pragmaInfo);
    StmtVisitor.super.accept(stmt);
  }

  public static @NotNull DefKind kindOf(@NotNull AnyVar var) {
    return switch (var) {
      case GeneralizedVar _ -> DefKind.Generalized;
      case DefVar<?, ?> defVar -> switch (defVar.concrete) {
        case FnDecl _ -> DefKind.Fn;
        case ClassMember _ -> DefKind.Member;
        case DataDecl _ -> DefKind.Data;
        case DataCon _ -> DefKind.Con;
        case PrimDecl _ -> DefKind.Prim;
        case ClassDecl _ -> DefKind.Clazz;
        default -> throw new Panic("unknown def type: " + defVar);
      };
      case LocalVar(_, _, GenerateKind.Generalized(_)) -> DefKind.Generalized;
      case LocalVar _ -> DefKind.LocalVar;
      default -> DefKind.Unknown;
    };
  }
}
