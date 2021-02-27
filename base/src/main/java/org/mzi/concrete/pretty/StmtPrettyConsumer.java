// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.concrete.pretty;

import org.glavo.kala.tuple.Unit;
import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.jetbrains.annotations.NotNull;
import org.mzi.concrete.Decl;
import org.mzi.concrete.Expr;
import org.mzi.concrete.Stmt;
import org.mzi.generic.Modifier;
import org.mzi.pretty.doc.Doc;

public class StmtPrettyConsumer implements Stmt.Visitor<Unit, Doc> {
  public static final StmtPrettyConsumer INSTANCE = new StmtPrettyConsumer();

  private Doc visitAccess(Stmt.@NotNull Accessibility accessibility) {
    return switch (accessibility) {
      case Public -> Doc.plain("\\public");
      case Private -> Doc.plain("\\private");
    };
  }

  @Override
  public Doc visitImport(Stmt.@NotNull ImportStmt cmd, Unit unit) {
    return Doc.cat(
      Doc.plain("\\import"),
      Doc.plain(" "),
      Doc.plain(cmd.path().joinToString("::")),
      Doc.plain(" "),
      Doc.plain("\\as"),
      Doc.plain(" "),
      Doc.plain(cmd.asName() == null ? cmd.path().joinToString("::") : cmd.asName())
    );
  }

  @Override
  public Doc visitOpen(Stmt.@NotNull OpenStmt cmd, Unit unit) {
    return Doc.cat(
      visitAccess(cmd.accessibility()),
      Doc.plain(" "),
      Doc.plain("\\open"),
      Doc.plain(" "),
      Doc.plain(cmd.path().joinToString("::")),
      Doc.plain(" "),
      switch (cmd.useHide().strategy()) {
        case Using -> Doc.plain("\\using");
        case Hiding -> Doc.plain("\\hiding");
      },
      Doc.plain("("),
      Doc.plain(cmd.useHide().list().joinToString(",")),
      Doc.plain(")")
    );
  }

  @Override
  public Doc visitModule(Stmt.@NotNull ModuleStmt mod, Unit unit) {
    return Doc.cat(
      visitAccess(mod.accessibility()),
      Doc.plain(" "),
      Doc.plain("\\module"),
      Doc.plain(" "),
      Doc.plain(mod.name()),
      Doc.plain(" {"),
      Doc.hardLine(),
      mod.contents().stream().map(Stmt::toDoc).reduce(Doc.empty(), Doc::vcat),
      Doc.hardLine(),
      Doc.plain("}")
    );
  }

  @Override
  public Doc visitDataDecl(Decl.@NotNull DataDecl decl, Unit unit) {
    // TODO[kiva]: implement
    return null;
  }

  @Override
  public Doc visitFnDecl(Decl.@NotNull FnDecl decl, Unit unit) {
    return Doc.cat(
      Doc.plain("\\def"),
      Doc.plain(" "),
      decl.modifiers.stream().map(this::visitModifier).reduce(Doc.empty(), Doc::hsep),
      Doc.plain(" "),
      Doc.plain(decl.ref.name()),
      Doc.plain(" "),
      visitTele(decl.telescope),
      decl.result instanceof Expr.HoleExpr
        ? Doc.plain(" ")
        : Doc.cat(Doc.plain(":"), decl.result.toDoc(), Doc.plain(" ")),
      Doc.plain("=>"),
      Doc.plain(" "),
      decl.body.toDoc(),
      decl.abuseBlock.sizeEquals(0)
        ? Doc.empty()
        : Doc.cat(Doc.plain(" "), Doc.plain("\\abusing"), Doc.plain(" "), visitAbuse(decl.abuseBlock))
    );
  }

  private Doc visitModifier(@NotNull Modifier modifier) {
    return switch (modifier) {
      case Inline -> Doc.plain("\\inlnie");
      case Erase -> Doc.plain("\\erase");
    };
  }

  /*package-private*/ Doc visitTele(@NotNull ImmutableSeq<Expr.Param> telescope) {
    return telescope.stream()
      .map(this::visitParam)
      .reduce(Doc.empty(), Doc::hsep);
  }

  /*package-private*/ Doc visitParam(@NotNull Expr.Param param) {
    return Doc.cat(
      param.explicit() ? Doc.plain("(") : Doc.plain("{"),
      Doc.plain(param.ref().name()),
      param.type() == null
        ? Doc.empty()
        : Doc.cat(Doc.plain(" : "), param.type().toDoc()),
      param.explicit() ? Doc.plain(")") : Doc.plain("}")
    );
  }

  private Doc visitAbuse(@NotNull ImmutableSeq<Stmt> block) {
    return block.sizeEquals(1)
      ? block.get(0).toDoc()
      : block.stream().map(Stmt::toDoc).reduce(Doc.empty(), Doc::vcat);
  }
}
