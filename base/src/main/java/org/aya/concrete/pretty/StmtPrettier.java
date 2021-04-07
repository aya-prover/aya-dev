// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.concrete.pretty;

import org.aya.concrete.*;
import org.aya.core.pretty.DefPrettier;
import org.aya.core.pretty.TermPrettier;
import org.aya.generic.Modifier;
import org.aya.pretty.doc.Doc;
import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.glavo.kala.tuple.Unit;
import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000, kiva, icey
 * @see org.aya.core.pretty.DefPrettier
 */
public final class StmtPrettier implements Signatured.Visitor<Unit, Doc>, Stmt.Visitor<Unit, Doc> {
  public static final @NotNull StmtPrettier INSTANCE = new StmtPrettier();

  private StmtPrettier() {
  }

  private Doc visitAccess(Stmt.@NotNull Accessibility accessibility) {
    return Doc.styled(TermPrettier.KEYWORD, accessibility.keyword);
  }

  @Override public Doc visitImport(Stmt.@NotNull ImportStmt cmd, Unit unit) {
    return Doc.cat(
      Doc.styled(TermPrettier.KEYWORD, "\\import"),
      Doc.plain(" "),
      Doc.symbol(cmd.path().joinToString("::")),
      Doc.plain(" "),
      Doc.styled(TermPrettier.KEYWORD, "\\as"),
      Doc.plain(" "),
      cmd.asName() == null ? Doc.symbol(cmd.path().joinToString("::")) : Doc.plain(cmd.asName())
    );
  }

  @Override public Doc visitOpen(Stmt.@NotNull OpenStmt cmd, Unit unit) {
    return Doc.cat(
      visitAccess(cmd.accessibility()),
      Doc.plain(" "),
      Doc.styled(TermPrettier.KEYWORD, "\\open"),
      Doc.plain(" "),
      Doc.plain(cmd.path().joinToString("::")),
      Doc.plain(" "),
      Doc.styled(TermPrettier.KEYWORD, switch (cmd.useHide().strategy()) {
        case Using -> "\\using ";
        case Hiding -> "\\hiding ";
      }),
      Doc.plain("("),
      Doc.plain(cmd.useHide().list().joinToString(", ")),
      Doc.plain(")")
    );
  }

  @Override public Doc visitModule(Stmt.@NotNull ModuleStmt mod, Unit unit) {
    return Doc.cat(
      visitAccess(mod.accessibility()),
      Doc.plain(" "),
      Doc.styled(TermPrettier.KEYWORD, "\\module"),
      Doc.plain(" "),
      Doc.plain(mod.name()),
      Doc.plain(" {"),
      Doc.hardLine(),
      Doc.vcat(mod.contents().stream().map(Stmt::toDoc)),
      Doc.hardLine(),
      Doc.plain("}")
    );
  }

  @Override public Doc visitData(Decl.@NotNull DataDecl decl, Unit unit) {
    return Doc.cat(
      visitAccess(decl.accessibility()),
      Doc.plain(" "),
      Doc.styled(TermPrettier.KEYWORD, "\\data"),
      Doc.plain(" "),
      Doc.plain(decl.ref.name()),
      visitTele(decl.telescope),
      decl.result instanceof Expr.HoleExpr
        ? Doc.empty()
        : Doc.cat(Doc.plain(" : "), decl.result.toDoc()),
      decl.body.isEmpty() ? Doc.empty()
        : Doc.cat(Doc.line(), Doc.indent(2, Doc.vcat(
        decl.body.stream().map(ctor -> ctor.accept(this, Unit.unit())))))
    );
  }

  @Override public void traceEntrance(@NotNull Decl decl, Unit unit) {
  }

  @Override public Doc visitCtor(Decl.@NotNull DataCtor ctor, Unit unit) {
    var doc = Doc.cat(
      ctor.coerce ? Doc.styled(TermPrettier.KEYWORD, "\\coerce ") : Doc.empty(),
      Doc.plain(ctor.ref.name()),
      visitTele(ctor.telescope),
      visitClauses(ctor.clauses, true)
    );
    if (ctor.patterns.isNotEmpty()) {
      var pats = Doc.join(Doc.plain(", "), ctor.patterns.stream().map(Pattern::toDoc));
      return Doc.hcat(Doc.plain("| "), pats, Doc.plain(" => "), doc);
    } else return Doc.hcat(Doc.plain("| "), doc);
  }

  private Doc visitClauses(@NotNull ImmutableSeq<Pattern.Clause> clauses, boolean wrapInBraces) {
    if (clauses.isEmpty()) return Doc.empty();
    var clausesDoc = Doc.vcat(
      clauses.stream()
        .map(PatternPrettier.INSTANCE::matchy)
        .map(doc -> Doc.hcat(Doc.plain("|"), doc)));
    return wrapInBraces ? Doc.wrap("{", "}", clausesDoc) : clausesDoc;
  }

  @Override public Doc visitStruct(@NotNull Decl.StructDecl decl, Unit unit) {
    return Doc.cat(
      visitAccess(decl.accessibility()),
      Doc.plain(" "),
      Doc.styled(TermPrettier.KEYWORD, "\\struct"),
      Doc.plain(" "),
      Doc.plain(decl.ref.name()),
      visitTele(decl.telescope),
      decl.result instanceof Expr.HoleExpr
        ? Doc.empty()
        : Doc.cat(Doc.plain(" : "), decl.result.toDoc()),
      decl.fields.isEmpty() ? Doc.empty()
        : Doc.cat(Doc.line(), Doc.indent(2, Doc.vcat(
        decl.fields.stream().map(field -> field.accept(this, Unit.unit())))))
    );
  }

  @Override public Doc visitField(Decl.@NotNull StructField field, Unit unit) {
    return Doc.hcat(
      Doc.plain("| "),
      field.coerce ? Doc.styled(TermPrettier.KEYWORD, "\\coerce ") : Doc.empty(),
      Doc.plain(field.ref.name()),
      visitTele(field.telescope),
      field.result instanceof Expr.HoleExpr
        ? Doc.empty()
        : Doc.cat(Doc.plain(" : "), field.result.toDoc()),
      field.body.isEmpty()
        ? Doc.empty()
        : Doc.cat(Doc.symbol(" => "), field.body.get().toDoc()),
      visitClauses(field.clauses, true)
    );
  }

  @Override public Doc visitFn(Decl.@NotNull FnDecl decl, Unit unit) {
    return Doc.cat(
      visitAccess(decl.accessibility()),
      Doc.plain(" "),
      Doc.styled(TermPrettier.KEYWORD, "\\def"),
      decl.modifiers.isEmpty() ? Doc.plain(" ") :
        decl.modifiers.stream().map(this::visitModifier).reduce(Doc.empty(), Doc::hsep),
      Doc.plain(decl.ref.name()),
      visitTele(decl.telescope),
      decl.result instanceof Expr.HoleExpr
        ? Doc.empty()
        : Doc.cat(Doc.plain(" : "), decl.result.toDoc()),
      decl.body.isLeft() ? Doc.symbol(" => ") : Doc.empty(),
      decl.body.fold(Expr::toDoc, clauses ->
        Doc.hcat(Doc.line(), Doc.indent(2, visitClauses(clauses, false)))),
      decl.abuseBlock.sizeEquals(0)
        ? Doc.empty()
        : Doc.cat(Doc.plain(" "), Doc.styled(TermPrettier.KEYWORD, "\\abusing"), Doc.plain(" "), visitAbuse(decl.abuseBlock))
    );
  }

  @Override public Doc visitPrim(@NotNull Decl.PrimDecl decl, Unit unit) {
    return DefPrettier.primDoc(decl.ref);
  }

  private Doc visitModifier(@NotNull Modifier modifier) {
    return Doc.styled(TermPrettier.KEYWORD, switch (modifier) {
      case Inline -> "\\inline";
      case Erase -> "\\erase";
    });
  }

  /*package-private*/ Doc visitTele(@NotNull ImmutableSeq<Expr.Param> telescope) {
    return telescope.isEmpty() ? Doc.empty() : Doc.cat(Doc.plain(" "),
      Doc.hsep(telescope.map(Expr.Param::toDoc)));
  }

  private Doc visitAbuse(@NotNull ImmutableSeq<Stmt> block) {
    return block.sizeEquals(1)
      ? block.get(0).toDoc()
      : Doc.vcat(block.stream().map(Stmt::toDoc));
  }
}
