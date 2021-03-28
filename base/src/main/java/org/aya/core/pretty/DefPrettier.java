// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.pretty;

import org.aya.api.ref.Var;
import org.aya.core.def.*;
import org.aya.core.pat.Pat;
import org.aya.core.term.Term;
import org.aya.generic.Matching;
import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Style;
import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.glavo.kala.tuple.Unit;
import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000
 * @see org.aya.concrete.pretty.StmtPrettier
 */
public final class DefPrettier implements Def.Visitor<Unit, @NotNull Doc> {
  public static final @NotNull DefPrettier INSTANCE = new DefPrettier();

  private DefPrettier() {
  }

  @Override public Doc visitFn(@NotNull FnDef def, Unit unit) {
    return Doc.cat(
      Doc.styled(TermPrettier.KEYWORD, "\\def "),
      link(def.ref(), TermPrettier.FN_CALL),
      visitTele(def.telescope()),
      Doc.plain(" : "), def.result().toDoc(),
      def.body().isLeft() ? Doc.plain(" => ") : Doc.empty(),
      def.body().fold(Term::toDoc, clauses ->
        Doc.hcat(Doc.line(), Doc.hang(2, visitClauses(clauses, false))))
    );
  }

  /*package-private*/ Doc visitTele(@NotNull ImmutableSeq<Term.Param> telescope) {
    return telescope.isEmpty() ? Doc.empty() : Doc.cat(Doc.plain(" "),
      Doc.hsep(telescope.view().map(Term.Param::toDoc)));
  }

  private Doc visitClauses(@NotNull ImmutableSeq<Matching<Pat, Term>> clauses, boolean wrapInBraces) {
    if (clauses.isEmpty()) return Doc.empty();
    var clausesDoc = Doc.vcat(
      clauses.view()
        .map(PatPrettier.INSTANCE::matchy)
        .map(doc -> Doc.hcat(Doc.plain("|"), doc)));
    return wrapInBraces ? Doc.wrap("{", "}", clausesDoc) : clausesDoc;
  }

  @Override public Doc visitData(@NotNull DataDef def, Unit unit) {
    return Doc.cat(
      Doc.styled(TermPrettier.KEYWORD, "\\data"),
      Doc.plain(" "),
      link(def.ref(), TermPrettier.DATA_CALL),
      visitTele(def.telescope()),
      Doc.plain(" : "), def.result().toDoc(),
      def.body().isEmpty() ? Doc.empty()
        : Doc.cat(Doc.line(), Doc.hang(2, Doc.vcat(
        def.body().view().map(ctor -> ctor.accept(this, Unit.unit())))))
    );
  }

  public static @NotNull Doc link(@NotNull Var ref, @NotNull Style color) {
    return Doc.hashCodeLink(Doc.styled(color, ref.name()), ref.hashCode());
  }

  public static @NotNull Doc plainLink(@NotNull Var ref) {
    return Doc.hashCodeLink(Doc.plain(ref.name()), ref.hashCode());
  }

  @Override public Doc visitCtor(@NotNull DataDef.Ctor ctor, Unit unit) {
    var doc = Doc.cat(
      ctor.coerce() ? Doc.styled(TermPrettier.KEYWORD, "\\coerce ") : Doc.empty(),
      link(ctor.ref(), TermPrettier.CON_CALL),
      visitTele(ctor.conTele()),
      visitClauses(ctor.clauses(), true)
    );
    if (ctor.pats().isNotEmpty()) {
      var pats = Doc.join(Doc.plain(", "), ctor.pats().stream().map(Pat::toDoc));
      return Doc.hcat(Doc.plain("| "), pats, Doc.plain(" => "), doc);
    } else return Doc.hcat(Doc.plain("| "), doc);
  }

  @Override public Doc visitStruct(@NotNull StructDef def, Unit unit) {
    throw new UnsupportedOperationException();
  }

  @Override public Doc visitField(@NotNull StructDef.Field def, Unit unit) {
    throw new UnsupportedOperationException();
  }

  @Override public @NotNull Doc visitPrim(@NotNull PrimDef def, Unit unit) {
    return primDoc(def.ref());
  }

  public static @NotNull Doc primDoc(Var ref) {
    return Doc.hcat(
      Doc.styled(TermPrettier.KEYWORD, "\\prim "),
      Doc.hashCodeLink(Doc.styled(TermPrettier.FN_CALL, ref.name()), ref.hashCode())
    );
  }
}
