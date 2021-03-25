// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.pretty;

import org.aya.core.def.DataDef;
import org.aya.core.def.Def;
import org.aya.core.def.FnDef;
import org.aya.core.def.StructDef;
import org.aya.core.pat.Pat;
import org.aya.core.term.Term;
import org.aya.generic.Matching;
import org.aya.pretty.doc.Doc;
import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.glavo.kala.tuple.Unit;
import org.jetbrains.annotations.NotNull;

public final class DefPrettier implements Def.Visitor<Unit, Doc> {
  public static final @NotNull DefPrettier INSTANCE = new DefPrettier();

  private DefPrettier() {
  }

  @Override public Doc visitFn(@NotNull FnDef def, Unit unit) {
    return Doc.cat(
      Doc.styled(TermPrettier.KEYWORD, "\\def "),
      Doc.styled(TermPrettier.FN_CALL, def.ref().name()),
      def.telescope().isEmpty() ? Doc.empty() :
        Doc.cat(Doc.plain(" "), visitTele(def.telescope())),
      Doc.plain(" : "), def.result().toDoc(),
      def.body().isLeft() ? Doc.plain(" => ") : Doc.empty(),
      def.body().fold(Term::toDoc, clauses ->
        Doc.hcat(Doc.line(), Doc.hang(2, visitClauses(clauses, false))))
    );
  }

  /*package-private*/ Doc visitTele(@NotNull ImmutableSeq<Term.Param> telescope) {
    return telescope.isEmpty() ? Doc.empty() : Doc.hsep(telescope.map(Term.Param::toDoc));
  }

  private Doc visitClauses(@NotNull ImmutableSeq<Matching<Pat, Term>> clauses, boolean wrapInBraces) {
    if (clauses.isEmpty()) return Doc.empty();
    var clausesDoc = Doc.vcat(
      clauses.stream()
        .map(PatPrettier.INSTANCE::matchy)
        .map(doc -> Doc.hcat(Doc.plain("| "), doc)));
    return wrapInBraces ? Doc.wrap("{", "}", clausesDoc) : clausesDoc;
  }

  @Override public Doc visitData(@NotNull DataDef def, Unit unit) {
    return Doc.cat(
      Doc.styled(TermPrettier.KEYWORD, "\\data"),
      Doc.plain(" "),
      Doc.styled(TermPrettier.DATA_CALL, def.ref().name()),
      Doc.plain(" "),
      visitTele(def.telescope()),
      Doc.plain(" : "), def.result().toDoc(),
      def.body().isEmpty() ? Doc.empty()
        : Doc.cat(Doc.line(), Doc.hang(2, Doc.vcat(
        def.body().stream().map(ctor -> ctor.accept(this, Unit.unit())))))
    );
  }

  @Override public Doc visitCtor(@NotNull DataDef.Ctor ctor, Unit unit) {
    var doc = Doc.cat(
      ctor.coerce() ? Doc.styled(TermPrettier.KEYWORD, "\\coerce ") : Doc.empty(),
      Doc.styled(TermPrettier.CON_CALL, ctor.ref().name()),
      Doc.plain(" "),
      visitTele(ctor.conTele()),
      visitClauses(ctor.clauses(), true)
    );
    if (!ctor.pats().isEmpty()) {
      var pats = Doc.join(Doc.plain(","), ctor.pats().stream().map(Pat::toDoc));
      return Doc.hcat(Doc.plain("| "), pats, Doc.plain(" => "), doc);
    } else return Doc.hcat(Doc.plain("| "), doc);
  }

  @Override public Doc visitStruct(@NotNull StructDef def, Unit unit) {
    throw new UnsupportedOperationException();
  }

  @Override public Doc visitField(@NotNull StructDef.Field def, Unit unit) {
    throw new UnsupportedOperationException();
  }
}
