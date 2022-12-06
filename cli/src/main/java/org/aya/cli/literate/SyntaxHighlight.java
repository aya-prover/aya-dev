// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.literate;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import kala.control.Option;
import kala.tuple.Tuple;
import kala.tuple.Tuple2;
import org.aya.cli.parse.AyaGKProducer;
import org.aya.concrete.Expr;
import org.aya.concrete.Pattern;
import org.aya.concrete.stmt.*;
import org.aya.concrete.visitor.StmtFolder;
import org.aya.core.def.*;
import org.aya.core.term.PiTerm;
import org.aya.generic.AyaDocile;
import org.aya.parser.AyaParserDefinitionBase;
import org.aya.ref.AnyVar;
import org.aya.ref.DefVar;
import org.aya.ref.GenerateKind;
import org.aya.ref.LocalVar;
import org.aya.tyck.ExprTycker;
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
    return add(acc, linkRef(pos, var, varType(var)));
  }

  @Override
  public @NotNull MutableList<HighlightInfo> fold(@NotNull MutableList<HighlightInfo> acc, @NotNull Expr expr) {
    return switch (expr) {
      case Expr.LitInt lit -> add(acc, HighlightInfo.LitKind.Int.toLit(lit.sourcePos()));
      case Expr.LitString lit -> add(acc, HighlightInfo.LitKind.String.toLit(lit.sourcePos()));
      case Expr.Ref ref -> add(acc, linkRef(ref.sourcePos(), ref.resolvedVar(),
        Option.ofNullable(ref.theCore().get()).map(ExprTycker.Result::type).getOrNull()));
      case Expr.Lambda lam -> tryLinkLocalDef(acc, lam.param());
      case Expr.Pi pi -> tryLinkLocalDef(acc, pi.param());
      case Expr.Sigma sigma -> sigma.params().foldLeft(acc, this::tryLinkLocalDef);
      default -> StmtFolder.super.fold(acc, expr);
    };
  }

  @Override
  public @NotNull MutableList<HighlightInfo> fold(@NotNull MutableList<HighlightInfo> acc, @NotNull Pattern pat) {
    return switch (pat) {
      case Pattern.Number num -> add(acc, HighlightInfo.LitKind.Int.toLit(num.sourcePos()));
      case Pattern.Bind bind -> add(acc, linkDef(bind.sourcePos(), bind.bind(), bind.type().get()));
      case Pattern.Ctor ctor -> {
        var resolved = ctor.resolved().data();
        var type = varType(resolved);
        yield add(acc, linkRef(ctor.resolved().sourcePos(), resolved, type));
      }
      default -> StmtFolder.super.fold(acc, pat);
    };
  }

  @Override
  public @NotNull MutableList<HighlightInfo> fold(@NotNull MutableList<HighlightInfo> acc, @NotNull Stmt stmt) {
    acc = StmtFolder.super.fold(acc, stmt);
    return switch (stmt) {
      case Generalize g -> g.variables.foldLeft(acc, (a, var) -> add(a, linkDef(var.sourcePos, var, null)));
      case Command.Module m -> add(acc, linkModuleDef(new QualifiedID(m.sourcePos(), m.name())));
      case Command.Import i -> add(acc, linkModuleRef(i.path()));
      case Command.Open o when o.fromSugar() -> acc;  // handled in `case Decl` or `case Command.Import`
      case Command.Open o -> add(acc, linkModuleRef(o.path()));
      case Decl decl -> {
        var declType = declType(decl);
        acc = declType._2
          .foldLeft(acc, (ac, p) -> tryLinkLocalDef(ac, p._1, p._2));
        yield add(acc, linkDef(decl.sourcePos(), decl.ref(), declType._1));
      }
    };
  }

  private Tuple2<AyaDocile, SeqView<Tuple2<LocalVar, AyaDocile>>> declType(@NotNull Decl decl) {
    var type = varType(decl.ref());
    // If it has core term, type is available.
    if (decl.ref().core instanceof Def def) return Tuple.of(type,
      def.telescope().view().map(p -> Tuple.of(p.ref(), p.type())));
    // If it is telescopic, type is unavailable.
    if (decl instanceof Decl.Telescopic<?> teleDecl) return Tuple.of(type,
      teleDecl.telescope().view().map(p -> Tuple.of(p.ref(), null)));
    return Tuple.of(null, SeqView.empty());
  }

  private @Nullable AyaDocile varType(@NotNull AnyVar var) {
    if (var instanceof DefVar<?, ?> defVar && defVar.core instanceof Def def)
      return PiTerm.make(def.telescope(), def.result());
    return null;
  }

  private @NotNull MutableList<HighlightInfo> tryLinkLocalDef(
    @NotNull MutableList<HighlightInfo> acc,
    @NotNull Expr.Param param
  ) {
    return tryLinkLocalDef(acc, param.ref(), param.type());
  }

  private @NotNull MutableList<HighlightInfo> tryLinkLocalDef(
    @NotNull MutableList<HighlightInfo> acc,
    @NotNull LocalVar var,
    @Nullable AyaDocile type
  ) {
    if (var.isGenerated()) {
      return acc;
    }

    return add(acc, linkDef(var.definition(), var, type));
  }

  private @NotNull HighlightInfo linkDef(@NotNull SourcePos sourcePos, @NotNull AnyVar var, @Nullable AyaDocile type) {
    return kindOf(var).toDef(sourcePos, var.hashCode(), type);
  }

  private @NotNull HighlightInfo linkRef(@NotNull SourcePos sourcePos, @NotNull AnyVar var, @Nullable AyaDocile type) {
    if (var instanceof LocalVar(var $, var $$, GenerateKind.Generalized(var origin)))
      return linkRef(sourcePos, origin, type);
    return kindOf(var).toRef(sourcePos, var.hashCode(), type);
  }

  private @NotNull HighlightInfo linkModuleRef(@NotNull QualifiedID id) {
    return HighlightInfo.DefKind.Module.toRef(id.sourcePos(), id.join().hashCode(), null);
  }

  private @NotNull HighlightInfo linkModuleDef(@NotNull QualifiedID id) {
    return HighlightInfo.DefKind.Module.toDef(id.sourcePos(), id.join().hashCode(), null);
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
