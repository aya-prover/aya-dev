// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.literate;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.control.Option;
import org.aya.cli.parse.AyaGKProducer;
import org.aya.concrete.remark.Remark;
import org.aya.concrete.stmt.*;
import org.aya.concrete.visitor.StmtFolder;
import org.aya.core.def.*;
import org.aya.parser.AyaParserDefinitionBase;
import org.aya.pretty.backend.string.LinkId;
import org.aya.ref.AnyVar;
import org.aya.ref.DefVar;
import org.aya.ref.LocalVar;
import org.aya.util.error.SourceFile;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;

import java.util.PriorityQueue;

/** @implNote Use {@link java.util.PriorityQueue} instead of {@link SeqView} for performance consideration. */
public class SyntaxHighlight implements StmtFolder<PriorityQueue<HighlightInfo>> {
  /** @param sourceFile If not null, provide keyword highlights too */
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
      var keywords = lexer.allTheWayDown().map(token -> new HighlightInfo(
        AyaGKProducer.sourcePosOf(token, file),
        new HighlightInfo.SymLit(HighlightInfo.LitKind.Keyword)));
      semantics = semantics.concat(keywords);
    }
    return semantics.sorted();
  }

  @Override public @NotNull PriorityQueue<HighlightInfo> init() {
    return new PriorityQueue<>();
  }

  @Override
  public @NotNull PriorityQueue<HighlightInfo> fold(@NotNull PriorityQueue<HighlightInfo> acc, @NotNull AnyVar var, @NotNull SourcePos pos) {
    acc.add(linkRef(pos, var));
    return acc;
  }

  @Override
  public @NotNull PriorityQueue<HighlightInfo> fold(@NotNull PriorityQueue<HighlightInfo> acc, @NotNull Stmt stmt) {
    switch (stmt) {
      case Generalize g -> g.variables.forEach(var -> acc.add(linkDef(var.sourcePos, var)));
      case Command.Import i -> acc.add(linkModuleDef(i.path()));
      case Command.Module m -> acc.add(linkModuleDef(new QualifiedID(m.sourcePos(), m.name())));
      case Command.Open o -> acc.add(linkModuleRef(o.path()));
      case ClassDecl decl -> acc.add(linkDef(decl.sourcePos, decl.ref()));
      case Decl decl -> acc.add(linkDef(decl.sourcePos(), decl.ref()));
      case Remark remark -> {} // TODO: highlight literate
    }
    return acc;
  }

  private @NotNull HighlightInfo linkDef(@NotNull SourcePos sourcePos, @NotNull AnyVar var) {
    return kindOf(var).toDef(sourcePos, new LinkId("#" + var.hashCode()));
  }

  private @NotNull HighlightInfo linkRef(@NotNull SourcePos sourcePos, @NotNull AnyVar var) {
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
      case LocalVar ignored -> HighlightInfo.DefKind.LocalVar;
      default -> HighlightInfo.DefKind.Unknown;
    };
  }
}
