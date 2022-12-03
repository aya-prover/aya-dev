// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.literate;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import kala.control.Option;
import org.aya.cli.parse.AyaGKProducer;
import org.aya.concrete.Expr;
import org.aya.concrete.Pattern;
import org.aya.concrete.remark.Remark;
import org.aya.concrete.stmt.*;
import org.aya.concrete.visitor.StmtFolder;
import org.aya.core.def.*;
import org.aya.parser.AyaParserDefinitionBase;
import org.aya.pretty.backend.string.LinkId;
import org.aya.ref.AnyVar;
import org.aya.ref.DefVar;
import org.aya.ref.GenerateKind;
import org.aya.ref.LocalVar;
import org.aya.util.error.SourceFile;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;

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
    var distiller = new SyntaxHighlight();
    var semantics = program.flatMap(distiller);
    if (sourceFile.isDefined()) {
      var file = sourceFile.get();
      var lexer = AyaParserDefinitionBase.createLexer(false);
      lexer.reset(file.sourceCode(), 0, file.sourceCode().length(), 0);
      var keywords = lexer.allTheWayDown()
        .view()
        .filter(x -> AyaParserDefinitionBase.KEYWORDS.contains(x.type()))
        .map(token -> {
          var sourcePos = AyaGKProducer.sourcePosOf(token, file);
          var type = new HighlightInfo.SymLit(HighlightInfo.LitKind.Keyword);
          return new HighlightInfo(sourcePos, type);
        });
      semantics = semantics.concat(keywords);
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

  @Override
  public @NotNull MutableList<HighlightInfo> fold(@NotNull MutableList<HighlightInfo> acc, @NotNull AnyVar var, @NotNull SourcePos pos) {
    return add(acc, linkRef(pos, var));
  }

  @Override
  public @NotNull MutableList<HighlightInfo> fold(@NotNull MutableList<HighlightInfo> acc, @NotNull Expr expr) {
    return switch (expr) {
      case Expr.LitInt lit -> add(acc, HighlightInfo.LitKind.Int.toLit(lit.sourcePos()));
      case Expr.LitString lit -> add(acc, HighlightInfo.LitKind.String.toLit(lit.sourcePos()));
      default -> StmtFolder.super.fold(acc, expr);
    };
  }

  @Override
  public @NotNull MutableList<HighlightInfo> fold(@NotNull MutableList<HighlightInfo> acc, @NotNull Pattern pat) {
    return switch (pat) {
      case Pattern.Number num -> add(acc, HighlightInfo.LitKind.Int.toLit(num.sourcePos()));
      case Pattern.Bind bind -> add(acc, linkDef(bind.sourcePos(), bind.bind()));
      default -> StmtFolder.super.fold(acc, pat);
    };
  }

  @Override
  public @NotNull MutableList<HighlightInfo> fold(@NotNull MutableList<HighlightInfo> acc, @NotNull Stmt stmt) {
    acc = StmtFolder.super.fold(acc, stmt);
    return switch (stmt) {
      case Generalize g -> g.variables.foldLeft(acc, (a, var) -> add(a, linkDef(var.sourcePos, var)));
      case Command.Module m -> add(acc, linkModuleDef(new QualifiedID(m.sourcePos(), m.name())));
      case Command.Import i -> add(acc, linkModuleRef(i.path()));
      case Command.Open o when o.fromSugar() -> acc;  // handled in `case Decl` or `case Command.Import`
      case Command.Open o -> add(acc, linkModuleRef(o.path()));
      case Decl decl -> {
        if (decl instanceof Decl.Telescopic<?> teleDecl) acc = teleDecl.telescope().view()
          .map(Expr.Param::ref)
          .filterNot(LocalVar::isGenerated)
          .foldLeft(acc, (ac, def) -> add(ac, linkDef(def.definition(), def)));
        yield add(acc, linkDef(decl.sourcePos(), decl.ref()));
      }
      case Remark remark -> acc; // TODO: highlight literate
    };
  }

  private @NotNull HighlightInfo linkDef(@NotNull SourcePos sourcePos, @NotNull AnyVar var) {
    return kindOf(var).toDef(sourcePos, new LinkId("#" + var.hashCode()));
  }

  private @NotNull HighlightInfo linkRef(@NotNull SourcePos sourcePos, @NotNull AnyVar var) {
    if (var instanceof LocalVar localVar
      && localVar.generateKind() instanceof GenerateKind.Generalized generalized) {
      return linkRef(sourcePos, generalized.origin());
    }

    return kindOf(var).toRef(sourcePos, new LinkId("#" + var.hashCode()));
  }

  private @NotNull HighlightInfo linkModuleRef(@NotNull QualifiedID id) {
    return HighlightInfo.DefKind.Module.toRef(id.sourcePos(), new LinkId("#" + id.join().hashCode()));
  }

  private @NotNull HighlightInfo linkModuleDef(@NotNull QualifiedID id) {
    return HighlightInfo.DefKind.Module.toDef(id.sourcePos(), new LinkId("#" + id.join().hashCode()));
  }

  @SuppressWarnings("DuplicateBranchesInSwitch")
  private @NotNull HighlightInfo.DefKind kindOf(@NotNull AnyVar var) {
    record P(Decl decl, GenericDef def) {}
    return switch (var) {
      case GeneralizedVar ignored -> HighlightInfo.DefKind.Generalized;
      case DefVar<?, ?> defVar -> switch (new P(defVar.concrete, defVar.core)) {
        case P(TeleDecl.FnDecl $, var $$) -> HighlightInfo.DefKind.Fn;
        case P(TeleDecl.StructDecl $, var $$) -> HighlightInfo.DefKind.Struct;
        case P(TeleDecl.StructField $, var $$) -> HighlightInfo.DefKind.Field;
        case P(TeleDecl.DataDecl $, var $$) -> HighlightInfo.DefKind.Data;
        case P(TeleDecl.DataCtor $, var $$) -> HighlightInfo.DefKind.Con;
        case P(TeleDecl.PrimDecl $, var $$) -> HighlightInfo.DefKind.Prim;
        case P(var $, FnDef $$) -> HighlightInfo.DefKind.Fn;
        case P(var $, StructDef $$) -> HighlightInfo.DefKind.Struct;
        case P(var $, FieldDef $$) -> HighlightInfo.DefKind.Field;
        case P(var $, DataDef $$) -> HighlightInfo.DefKind.Data;
        case P(var $, CtorDef $$) -> HighlightInfo.DefKind.Con;
        case P(var $, PrimDef $$) -> HighlightInfo.DefKind.Prim;
        default -> HighlightInfo.DefKind.Unknown;
      };
      case LocalVar(var $, var $$, GenerateKind.Generalized(var $$$)) -> HighlightInfo.DefKind.Generalized;
      case LocalVar ignored -> HighlightInfo.DefKind.LocalVar;
      default -> HighlightInfo.DefKind.Unknown;
    };
  }
}
