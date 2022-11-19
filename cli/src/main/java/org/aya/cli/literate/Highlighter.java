// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.literate;

import org.aya.cli.literate.HighlightInfoType.Lit;
import org.aya.concrete.Expr;
import org.aya.concrete.Pattern;
import org.aya.concrete.remark.Remark;
import org.aya.concrete.stmt.*;
import org.aya.concrete.visitor.StmtConsumer;
import org.aya.core.def.*;
import org.aya.pretty.doc.Doc;
import org.aya.ref.AnyVar;
import org.aya.ref.DefVar;
import org.aya.ref.LocalVar;
import org.aya.util.distill.DistillerOptions;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static org.aya.cli.literate.HighlightInfoType.*;

public class Highlighter implements StmtConsumer {
  private final @NotNull HighlightInfoHolder holder = new HighlightInfoHolder();
  private final @NotNull DistillerOptions options;

  public Highlighter(@NotNull DistillerOptions options) {
    this.options = options;
  }

  /// region Stmt

  @Override
  public void accept(@NotNull Stmt stmt) {
    switch (stmt) {
      case Generalize generalize -> acceptGeneralize(generalize);
      case Command command -> acceptCommand(command);
      case Decl decl -> acceptDecl(decl);
      case Remark remark -> {}
    }

    StmtConsumer.super.accept(stmt);
  }

  private void acceptCommand(@NotNull Command command) {
    // TODO
  }

  private void acceptGeneralize(@NotNull Generalize generalize) {
    generalize.variables.forEach(var -> highlightVarDef(var, DefKind.Generalized));
  }

  @SuppressWarnings("unused")
  private void acceptDecl(@NotNull Decl decl) {
    var kind = switch (decl) {
      case TeleDecl.DataDecl dataDecl -> DefKind.Data;
      case TeleDecl.FnDecl fnDecl -> DefKind.Fn;
      case TeleDecl.PrimDecl primDecl -> DefKind.Prim;
      case TeleDecl.StructDecl structDecl -> DefKind.Struct;
      case TeleDecl.DataCtor dataCtor -> DefKind.Con;
      case TeleDecl.StructField structField -> DefKind.Field;
      case ClassDecl classDecl -> throw new UnsupportedOperationException("TODO");
    };

    highlightVarDef(decl.ref(), kind);
  }

  /// endregion

  /// region Var

  private void highlightVarDef(@NotNull AnyVar var, @Nullable DefKind kind) {
    var sourcePos = switch (var) {
      case GeneralizedVar genVar -> genVar.sourcePos;
      case DefVar<?, ?> defVar -> {
        var concrete = defVar.concrete;
        assert concrete != null;

        yield concrete.sourcePos();
      }
      case LocalVar localVar -> localVar.definition();
      default -> throw new UnsupportedOperationException("Unexpected var: " + var.getClass());
    };

    linkDef(sourcePos, var, kind);
  }

  private void highlightVarRef(@NotNull SourcePos sourcePos, @NotNull AnyVar var) {
    highlightVarRef(sourcePos, var, kindOf(var));
  }

  private void highlightVarRef(@NotNull SourcePos sourcePos, @NotNull AnyVar var, @Nullable DefKind kind) {
    linkRef(sourcePos, var, kind);
  }

  /**
   * @see org.aya.pretty.doc.Doc#linkDef(Doc, int)
   */
  private void linkDef(@NotNull SourcePos sourcePos, @NotNull AnyVar var, @Nullable DefKind style) {
    addInfo(sourcePos, new SymDef(
      String.valueOf(var.hashCode()),
      style
    ));
  }

  private void linkRef(@NotNull SourcePos sourcePos, @NotNull AnyVar var, @Nullable DefKind style) {
    addInfo(sourcePos, new SymRef(
      String.valueOf(var.hashCode()),
      style
    ));
  }

  /// endregion

  /// region Expr

  @Override
  @SuppressWarnings("unused")
  public @NotNull Expr pre(@NotNull Expr expr) {
    switch (expr) {
      // leaves
      case Expr.Ref ref -> highlightVarRef(ref.sourcePos(), ref.resolvedVar());
      case Expr.RawSort rawSort -> highlightSort(rawSort.sourcePos());
      case Expr.Sort sort -> {
        var sourcePos = switch (sort) {
          case Expr.ISet iSet -> iSet.sourcePos();
          case Expr.Prop prop -> prop.sourcePos();
          // Also highlight the number after `Type/Set`
          case Expr.Set set -> set.sourcePos();
          case Expr.Type type -> type.sourcePos();
        };

        highlightSort(sourcePos);
      }
      case Expr.LitInt litInt -> addInfo(litInt.sourcePos(), new Lit(LitKind.Int));
      case Expr.LitString litString -> addInfo(litString.sourcePos(), new Lit(LitKind.String));
      case Expr.Error error -> highlightError(error.sourcePos(), error.description().toDoc(options));
      case Expr.Unresolved unresolved -> highlightError(unresolved.sourcePos(), null);
      // TODO: how to impl?
      case Expr.Lift lift -> {}
      case Expr.Proj proj -> {}
      case Expr.RawProj rawProj -> {}
      // only highlight keyword (but this will be done in another place)
      case Expr.Coe coe -> {}
      case Expr.Do aDo -> {}
      case Expr.Pi pi -> {}
      case Expr.New aNew -> {}
      case Expr.Match match -> {}
      // no-op
      case Expr.Array array -> {}
      case Expr.Idiom idiom -> {}
      case Expr.Hole hole -> {}
      case Expr.Lambda lambda -> {}
      case Expr.Path path -> {}
      case Expr.PartEl partEl -> {}
      case Expr.BinOpSeq binOpSeq -> {}
      case Expr.App app -> {}
      case Expr.Sigma sigma -> {}
      case Expr.Tuple tuple -> {}
    }

    return StmtConsumer.super.pre(expr);
  }


  /// endregion

  /// region Pattern

  @Override
  @SuppressWarnings("unused")
  public @NotNull Pattern pre(@NotNull Pattern pattern) {
    switch (pattern) {
      case Pattern.Bind bind -> highlightVarDef(bind.bind(), DefKind.Local);
      case Pattern.Ctor ctor -> {
        var resolved = ctor.resolved();
        highlightVarRef(resolved.sourcePos(), resolved.data(), DefKind.Con);
      }
      case Pattern.Number number -> addInfo(number.sourcePos(), new Lit(LitKind.Int));
      // no-op
      case Pattern.BinOpSeq binOpSeq -> {}
      case Pattern.CalmFace calmFace -> {}
      case Pattern.List list -> {}
      case Pattern.Tuple tuple -> {}
      case Pattern.Absurd absurd -> {}
    }

    return StmtConsumer.super.pre(pattern);
  }

  /// endregion

  /// region Helper

  private void highlightError(@NotNull SourcePos sourcePos, @Nullable Doc description) {
    addInfo(sourcePos, new SymError(description));
  }

  private void highlightKeyword(@NotNull SourcePos sourcePos) {
    addInfo(sourcePos, new Lit(LitKind.Keyword));
  }

  private void highlightSort(@NotNull SourcePos sourcePos) {
    addInfo(sourcePos, new Lit(LitKind.Sort));
  }

  private void addInfo(@NotNull HighlightInfo info) {
    holder.addInfo(info);
  }

  private void addInfo(@NotNull SourcePos sourcePos, @NotNull HighlightInfoType type) {
    holder.addInfo(new HighlightInfo(sourcePos, type));
  }

  private @Nullable DefKind kindOf(@NotNull AnyVar var) {
    return switch (var) {
      case GeneralizedVar ignored -> DefKind.Generalized;
      case DefVar<?, ?> defVar -> {
        var concrete = defVar.concrete;
        var core = defVar.core;

        if (concrete instanceof TeleDecl.DataDecl || core instanceof DataDef) {
          yield DefKind.Data;
        } else if (concrete instanceof TeleDecl.DataCtor || core instanceof CtorDef) {
          yield DefKind.Con;
        } else if (concrete instanceof TeleDecl.StructDecl || core instanceof StructDef) {
          yield DefKind.Struct;
        } else if (concrete instanceof TeleDecl.StructField || core instanceof FieldDef) {
          yield DefKind.Field;
        } else if (concrete instanceof TeleDecl.FnDecl || core instanceof FnDef) {
          yield DefKind.Fn;
        } else if (concrete instanceof TeleDecl.PrimDecl || core instanceof PrimDef) {
          yield DefKind.Prim;
        } else {
          yield null;
        }
      }
      case LocalVar ignored -> DefKind.Local;
      default -> null;
    };
  }

  /// endregion

  /// region others

  public @NotNull HighlightInfoHolder result() {
    return this.holder;
  }

  /// endregion
}
