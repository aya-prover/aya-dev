// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.literate;

import com.intellij.psi.TokenType;
import com.intellij.psi.tree.TokenSet;
import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import kala.control.Option;
import kala.value.LazyValue;
import org.aya.cli.literate.HighlightInfo.DefKind;
import org.aya.cli.literate.HighlightInfo.Lit;
import org.aya.cli.literate.HighlightInfo.LitKind;
import org.aya.cli.parse.AyaProducer;
import org.aya.concrete.Expr;
import org.aya.concrete.Pattern;
import org.aya.concrete.stmt.GeneralizedVar;
import org.aya.concrete.stmt.Stmt;
import org.aya.concrete.stmt.decl.ClassDecl;
import org.aya.concrete.stmt.decl.TeleDecl;
import org.aya.concrete.visitor.StmtFolder;
import org.aya.core.def.*;
import org.aya.core.term.Term;
import org.aya.generic.AyaDocile;
import org.aya.parser.AyaParserDefinitionBase;
import org.aya.parser.ParserDefBase;
import org.aya.prettier.BasePrettier;
import org.aya.pretty.doc.Link;
import org.aya.ref.AnyVar;
import org.aya.ref.DefVar;
import org.aya.ref.GenerateKind;
import org.aya.ref.LocalVar;
import org.aya.resolve.context.ModuleName;
import org.aya.util.error.InternalException;
import org.aya.util.error.SourceFile;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** @implNote Use {@link MutableList} instead of {@link SeqView} for performance consideration. */
public record SyntaxHighlight(
  @Nullable ImmutableSeq<String> currentFileModule) implements StmtFolder<MutableList<HighlightInfo>> {
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
    @Nullable ImmutableSeq<String> currentFileModule,
    @NotNull Option<SourceFile> sourceFile,
    @NotNull ImmutableSeq<Stmt> program
  ) {
    var prettier = new SyntaxHighlight(currentFileModule);
    var semantics = program.flatMap(prettier);
    if (sourceFile.isDefined()) {
      var file = sourceFile.get();
      var lexer = AyaParserDefinitionBase.createLexer(false);
      lexer.reset(file.sourceCode(), 0, file.sourceCode().length(), 0);
      var addition = lexer.allTheWayDown().view()
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
        }).toImmutableSeq();
      semantics = semantics.concat(addition);
    }
    return semantics;
  }

  private @NotNull MutableList<HighlightInfo> add(@NotNull MutableList<HighlightInfo> x, @NotNull HighlightInfo info) {
    x.append(info);
    return x;
  }

  @Override public @NotNull MutableList<HighlightInfo> init() {
    return MutableList.create();
  }

  @Override public @NotNull MutableList<HighlightInfo> foldVarRef(
    @NotNull MutableList<HighlightInfo> acc,
    @NotNull AnyVar var, @NotNull SourcePos pos,
    @NotNull LazyValue<@Nullable Term> type
  ) {
    return add(acc, linkRef(pos, var, type.get()));
  }

  @Override public @NotNull MutableList<HighlightInfo> foldVarDecl(
    @NotNull MutableList<HighlightInfo> acc,
    @NotNull AnyVar var, @NotNull SourcePos pos,
    @NotNull LazyValue<@Nullable Term> type
  ) {
    if (var instanceof LocalVar v && v.isGenerated()) return acc;
    return add(acc, linkDef(pos, var, type.get()));
  }

  @Override
  public @NotNull MutableList<HighlightInfo> fold(@NotNull MutableList<HighlightInfo> acc, @NotNull Expr expr) {
    return switch (expr) {
      case Expr.LitInt lit -> add(acc, LitKind.Int.toLit(lit.sourcePos()));
      case Expr.LitString lit -> add(acc, LitKind.String.toLit(lit.sourcePos()));
      default -> StmtFolder.super.fold(acc, expr);
    };
  }

  @Override
  public @NotNull MutableList<HighlightInfo> fold(@NotNull MutableList<HighlightInfo> acc, @NotNull Pattern pat) {
    return switch (pat) {
      case Pattern.Number(var pos, _) -> add(acc, LitKind.Int.toLit(pos));
      default -> StmtFolder.super.fold(acc, pat);
    };
  }

  @Override
  public @NotNull MutableList<HighlightInfo> foldModuleRef(@NotNull MutableList<HighlightInfo> acc, @NotNull SourcePos pos, @NotNull ModuleName path) {
    // TODO: in SimpleModule.aya line 7, `public open Nat::N` is wrongly linked as `Link.cross`
    //  Because `Stmt.Open` does not provide the fully qualified module name (SimpleModule::Nat::N),
    //  instead, it provides `Nat::N` only, which is not enough to determine whether it is a cross-link or not.
    var link = currentFileModule != null && currentFileModule.sameElements(path.ids())
      ? Link.loc(path.toString())             // referring to a module defined in the current file
      : Link.cross(path.ids(), null);  // referring to another file-level module
    return add(acc, DefKind.Module.toRef(pos, link, null));
  }

  @Override
  public @NotNull MutableList<HighlightInfo> foldModuleDecl(@NotNull MutableList<HighlightInfo> acc, @NotNull SourcePos pos, @NotNull ModuleName path) {
    // module declaration is always a local link; we have no way to define a file-level module in aya code.
    return add(acc, DefKind.Module.toDef(pos, Link.loc(path.toString()), null));
  }

  private @NotNull HighlightInfo linkDef(@NotNull SourcePos sourcePos, @NotNull AnyVar var, @Nullable AyaDocile type) {
    return kindOf(var).toDef(sourcePos, BasePrettier.linkIdOf(currentFileModule, var), type);
  }

  private @NotNull HighlightInfo linkRef(@NotNull SourcePos sourcePos, @NotNull AnyVar var, @Nullable AyaDocile type) {
    if (var instanceof LocalVar(var _, var _, GenerateKind.Generalized(var origin)))
      return linkRef(sourcePos, origin, type);
    return kindOf(var).toRef(sourcePos, BasePrettier.linkIdOf(currentFileModule, var), type);
  }

  public static @NotNull DefKind kindOf(@NotNull AnyVar var) {
    return switch (var) {
      case GeneralizedVar $ -> DefKind.Generalized;
      case DefVar<?, ?> defVar -> {
        if (defVar.concrete instanceof TeleDecl.FnDecl || defVar.core instanceof FnDef)
          yield DefKind.Fn;
        else if (defVar.concrete instanceof TeleDecl.ClassMember || defVar.core instanceof MemberDef)
          yield DefKind.Member;
        else if (defVar.concrete instanceof TeleDecl.DataDecl || defVar.core instanceof DataDef)
          yield DefKind.Data;
        else if (defVar.concrete instanceof TeleDecl.DataCtor || defVar.core instanceof CtorDef)
          yield DefKind.Con;
        else if (defVar.concrete instanceof TeleDecl.PrimDecl || defVar.core instanceof PrimDef)
          yield DefKind.Prim;
        else if (defVar.concrete instanceof ClassDecl || defVar.core instanceof ClassDef)
          yield DefKind.Clazz;
        else throw new InternalException(STR."unknown def type: \{defVar}");
      }
      case LocalVar(_, _, GenerateKind.Generalized(_)) -> DefKind.Generalized;
      case LocalVar $ -> DefKind.LocalVar;
      default -> DefKind.Unknown;
    };
  }
}
