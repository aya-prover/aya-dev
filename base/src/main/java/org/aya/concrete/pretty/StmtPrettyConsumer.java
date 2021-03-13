// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.concrete.pretty;

import org.aya.concrete.Decl;
import org.aya.concrete.Expr;
import org.aya.concrete.Pattern;
import org.aya.concrete.Stmt;
import org.aya.generic.Modifier;
import org.aya.pretty.doc.Doc;
import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.glavo.kala.control.Either;
import org.glavo.kala.tuple.Unit;
import org.jetbrains.annotations.NotNull;

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
        case Using -> Doc.plain("\\using ");
        case Hiding -> Doc.plain("\\hiding ");
      },
      Doc.plain("("),
      Doc.plain(cmd.useHide().list().joinToString(", ")),
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
    return Doc.cat(
      visitAccess(decl.accessibility()),
      Doc.plain(" "),
      Doc.plain("\\data"),
      Doc.plain(" "),
      Doc.plain(decl.ref.name()),
      Doc.plain(" "),
      visitTele(decl.telescope),
      decl.result instanceof Expr.HoleExpr
        ? Doc.empty()
        : Doc.cat(Doc.plain(" : "), decl.result.toDoc()),
      Doc.hang(2, visitDataBody(decl.body))
    );
  }

  private Doc visitDataBody(Either<Decl.DataDecl.Ctors, Decl.DataDecl.Clauses> body) {
    return body.isLeft() ? visitDataCtors(body.getLeftValue()) : visitDataClauses(body.getRightValue());
  }

  private Doc visitDataClauses(Decl.DataDecl.Clauses clauses) {
    return clauses.clauses().stream()
      .map(c -> Doc.cat(Doc.plain("| "), c._1.toDoc(), Doc.plain(" => "), visitDataCtor(c._2)))
      .reduce(Doc.empty(), Doc::vcat);
  }

  private Doc visitDataCtors(Decl.DataDecl.Ctors ctors) {
    return ctors.ctors().stream()
      .map(this::visitDataCtor)
      .reduce(Doc.empty(), Doc::vcat);
  }

  private Doc visitDataCtor(Decl.DataCtor ctor) {
    return Doc.cat(
      Doc.plain("| "),
      ctor.coerce ? Doc.plain("\\coerce ") : Doc.empty(),
      Doc.plain(ctor.ref.name()),
      Doc.plain(" "),
      visitTele(ctor.telescope),
      visitClauses(ctor.clauses, true)
    );
  }

  private Doc visitClauses(@NotNull ImmutableSeq<Pattern.Clause> clauses, boolean wrapInBraces) {
    if (clauses.isEmpty()) return Doc.empty();
    var clausesDoc = Doc.cat(
      Doc.plain("| "), // join will only insert "|" between clauses
      Doc.join(Doc.plain("| "), clauses.stream()
        .map(PatternPrettyConsumer.INSTANCE::matchy)));
    return wrapInBraces ? Doc.wrap("{", "}", clausesDoc) : clausesDoc;
  }

  @Override
  public Doc visitFnDecl(Decl.@NotNull FnDecl decl, Unit unit) {
    return Doc.cat(
      visitAccess(decl.accessibility()),
      Doc.plain(" "),
      Doc.plain("\\def"),
      decl.modifiers.isEmpty() ? Doc.plain(" ") :
        decl.modifiers.stream().map(this::visitModifier).reduce(Doc.empty(), Doc::hsep),
      Doc.plain(decl.ref.name()),
      decl.telescope.isEmpty() ? Doc.empty() :
        Doc.cat(Doc.plain(" "), visitTele(decl.telescope)),
      decl.result instanceof Expr.HoleExpr
        ? Doc.plain(" ")
        : Doc.cat(Doc.plain(" : "), decl.result.toDoc(), Doc.plain(" ")),
      decl.body.isLeft() ? Doc.plain("=> ") : Doc.empty(),
      decl.body.fold(Expr::toDoc, clauses -> visitClauses(clauses, false)),
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
    return telescope
      .map(this::visitParam)
      .fold(Doc.empty(), Doc::hsep);
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
