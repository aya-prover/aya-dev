// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.error;

import kala.collection.immutable.ImmutableSeq;
import org.aya.pretty.doc.Doc;
import org.aya.syntax.core.term.ErrorTerm;
import org.aya.syntax.core.term.FreeTerm;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.core.term.call.MetaCall;
import org.aya.syntax.ref.LocalVar;
import org.aya.syntax.ref.MetaVar;
import org.aya.tyck.TyckState;
import org.aya.tyck.tycker.Stateful;
import org.aya.util.error.SourcePos;
import org.aya.util.prettier.PrettierOptions;
import org.aya.util.reporter.Problem;
import org.jetbrains.annotations.NotNull;

public record Goal(
  @Override @NotNull TyckState state, @NotNull MetaCall hole,
  @NotNull ImmutableSeq<LocalVar> scope
) implements Problem, Stateful {
  @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
    var meta = hole.ref();
    var result = meta.req() instanceof MetaVar.OfType(var type) ? freezeHoles(type)
      : new ErrorTerm(_ -> Doc.plain("???"));
    var doc = Doc.vcatNonEmpty(
      Doc.english("Goal of type"),
      Doc.par(1, result.toDoc(options)),
      Doc.par(1, Doc.parened(Doc.sep(Doc.plain("WHNF:"), whnf(result).toDoc(options)))),
      Doc.plain("Context:"),
      Doc.vcat(hole.args().map(arg -> renderScopeVar(options, arg)))
      // ,meta.conditions.isNotEmpty() ? Doc.vcat(
      //   ImmutableSeq.of(Doc.plain("To ensure confluence:"))
      //     .concat(meta.conditions.toImmutableSeq().map(tup -> Doc.par(1, Doc.cat(
      //       Doc.plain("Given "),
      //       Doc.parened(tup.component1().toDoc(options)),
      //       Doc.plain(", we should have: "),
      //       tup.component2().freezeHoles(state).toDoc(options)
      //     )))))
      //   : Doc.empty()
    );
    var metas = state.solutions();
    return !metas.containsKey(meta) ? doc :
      Doc.vcat(Doc.plain("Candidate exists:"), Doc.par(1, metas.get(meta).toDoc(options)), doc);
  }
  private @NotNull Doc renderScopeVar(@NotNull PrettierOptions options, Term arg) {
    var paramDoc = freezeHoles(arg).toDoc(options);
    var scopeInfo = arg instanceof FreeTerm(var ref) && scope.contains(ref)
      ? paramDoc : Doc.sep(paramDoc, Doc.parened(Doc.english("not in scope")));
    return Doc.par(1, scopeInfo);
  }

  @Override public @NotNull SourcePos sourcePos() { return hole.ref().pos(); }
  @Override public @NotNull Severity level() { return Severity.GOAL; }
  @Override public @NotNull Stage stage() { return Stage.TYCK; }
}
