// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.literate;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import kala.control.Option;
import kala.value.LazyValue;
import org.aya.cli.literate.HighlightInfo.DefKind;
import org.aya.cli.literate.HighlightInfo.LitKind;
import org.aya.cli.parse.AyaProducer;
import org.aya.concrete.Expr;
import org.aya.concrete.Pattern;
import org.aya.concrete.stmt.GeneralizedVar;
import org.aya.concrete.stmt.Stmt;
import org.aya.concrete.stmt.decl.Decl;
import org.aya.concrete.stmt.decl.TeleDecl;
import org.aya.concrete.visitor.StmtFolder;
import org.aya.core.def.*;
import org.aya.core.term.Term;
import org.aya.generic.AyaDocile;
import org.aya.parser.AyaParserDefinitionBase;
import org.aya.prettier.BasePrettier;
import org.aya.pretty.doc.Link;
import org.aya.ref.AnyVar;
import org.aya.ref.DefVar;
import org.aya.ref.GenerateKind;
import org.aya.ref.LocalVar;
import org.aya.resolve.context.ModulePath;
import org.aya.util.error.SourceFile;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** @implNote Use {@link MutableList} instead of {@link SeqView} for performance consideration. */
public class SyntaxHighlight implements StmtFolder<MutableList<HighlightInfo>> {
  /**
   * @param sourceFile If not null, provide keyword highlights too
   * @return a list of {@link HighlightInfo}, no order was expected, the elements may be duplicated
   */
  public static @NotNull ImmutableSeq<HighlightInfo> highlight(
    @NotNull Option<SourceFile> sourceFile,
    @NotNull ImmutableSeq<Stmt> program
  ) {
    var prettier = new SyntaxHighlight();
    var semantics = program.flatMap(prettier);
    if (sourceFile.isDefined()) {
      var file = sourceFile.get();
      var lexer = AyaParserDefinitionBase.createLexer(false);
      lexer.reset(file.sourceCode(), 0, file.sourceCode().length(), 0);
      var addition = lexer.allTheWayDown().view()
        .mapNotNull(token -> {
          var sourcePos = AyaProducer.sourcePosOf(token, file);
          var tokenType = token.type();
          if (AyaParserDefinitionBase.KEYWORDS.contains(tokenType))
            return new HighlightInfo.SymLit(LitKind.Keyword).toInfo(sourcePos);
          if (AyaParserDefinitionBase.SKIP_COMMENTS.contains(tokenType))
            return new HighlightInfo.SymLit(LitKind.Comment).toInfo(sourcePos);
          if (AyaParserDefinitionBase.UNICODES.contains(tokenType)
            || AyaParserDefinitionBase.MARKERS.contains(tokenType))
            return new HighlightInfo.SymLit(LitKind.SpecialSymbol).toInfo(sourcePos);
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
      case Pattern.Number(var pos, var $) -> add(acc, LitKind.Int.toLit(pos));
      default -> StmtFolder.super.fold(acc, pat);
    };
  }

  @Override
  public @NotNull MutableList<HighlightInfo> foldModuleRef(@NotNull MutableList<HighlightInfo> acc, @NotNull SourcePos pos, @NotNull ModulePath path) {
    // TODO: use `LinkId.page` for cross module link
    return add(acc, DefKind.Module.toRef(pos, Link.loc(path.toString()), null));
  }

  @Override
  public @NotNull MutableList<HighlightInfo> foldModuleDecl(@NotNull MutableList<HighlightInfo> acc, @NotNull SourcePos pos, @NotNull ModulePath path) {
    // TODO: use `LinkId.page` for cross module link
    return add(acc, DefKind.Module.toDef(pos, Link.loc(path.toString()), null));
  }

  private @NotNull HighlightInfo linkDef(@NotNull SourcePos sourcePos, @NotNull AnyVar var, @Nullable AyaDocile type) {
    return kindOf(var).toDef(sourcePos, BasePrettier.linkIdOf(var), type);
  }

  private @NotNull HighlightInfo linkRef(@NotNull SourcePos sourcePos, @NotNull AnyVar var, @Nullable AyaDocile type) {
    if (var instanceof LocalVar(var $, var $$, GenerateKind.Generalized(var origin)))
      return linkRef(sourcePos, origin, type);
    return kindOf(var).toRef(sourcePos, BasePrettier.linkIdOf(var), type);
  }

  @SuppressWarnings("unused")
  public static @NotNull DefKind kindOf(@NotNull AnyVar var) {
    record P(Decl decl, GenericDef def) {}
    return switch (var) {
      case GeneralizedVar ignored -> DefKind.Generalized;
      case DefVar<?, ?> defVar -> switch (new P(defVar.concrete, defVar.core)) {
        case P(TeleDecl.FnDecl $, var $$) -> DefKind.Fn;
        case P(TeleDecl.StructDecl $, var $$) -> DefKind.Struct;
        case P(TeleDecl.StructField $, var $$) -> DefKind.Field;
        case P(TeleDecl.DataDecl $, var $$) -> DefKind.Data;
        case P(TeleDecl.DataCtor $, var $$) -> DefKind.Con;
        case P(TeleDecl.PrimDecl $, var $$) -> DefKind.Prim;
        case P(var $, FnDef $$) -> DefKind.Fn;
        case P(var $, StructDef $$) -> DefKind.Struct;
        case P(var $, FieldDef $$) -> DefKind.Field;
        case P(var $, DataDef $$) -> DefKind.Data;
        case P(var $, CtorDef $$) -> DefKind.Con;
        case P(var $, PrimDef $$) -> DefKind.Prim;
        default -> DefKind.Unknown;
      };
      case LocalVar(var $, var $$, GenerateKind.Generalized(var $$$)) -> DefKind.Generalized;
      case LocalVar ignored -> DefKind.LocalVar;
      default -> DefKind.Unknown;
    };
  }
}
