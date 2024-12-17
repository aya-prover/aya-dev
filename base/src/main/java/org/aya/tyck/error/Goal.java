// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.error;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import org.aya.pretty.doc.Doc;
import org.aya.syntax.core.Jdg;
import org.aya.syntax.core.term.ErrorTerm;
import org.aya.syntax.core.term.FreeTerm;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.core.term.call.MetaCall;
import org.aya.syntax.ref.LocalCtx;
import org.aya.syntax.ref.LocalVar;
import org.aya.syntax.ref.MetaVar;
import org.aya.tyck.TyckState;
import org.aya.tyck.tycker.Stateful;
import org.aya.util.error.SourcePos;
import org.aya.util.prettier.PrettierOptions;
import org.aya.util.reporter.Problem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record Goal(
  @Override @NotNull TyckState state, @NotNull MetaCall hole,
  @Nullable Jdg filling, @NotNull LocalCtx ctx, @NotNull ImmutableSeq<LocalVar> scope
) implements Problem, Stateful {
  @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
    var meta = hole.ref();
    var result = meta.req() instanceof MetaVar.OfType(var type) ? freezeHoles(MetaCall.appType(hole, type))
      : new ErrorTerm(_ -> Doc.plain("???"));
    var lines = MutableList.of(
      Doc.english("Goal of type"),
      Doc.par(1, result.toDoc(options)),
      Doc.par(1, Doc.parened(Doc.sep(Doc.plain("Normalized:"),
        fullNormalize(result).toDoc(options)))),
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
    var metas = state.solutions;
    if (metas.containsKey(meta)) {
      lines.insert(0, Doc.par(1, metas.get(meta).toDoc(options)));
      lines.insert(0, Doc.plain("Candidate exists:"));
    }
    if (filling != null) {
      lines.append(Doc.english("You are trying:"));
      lines.append(Doc.par(1, filling.wellTyped().toDoc(options)));
      lines.append(Doc.english("It has type:"));
      lines.append(Doc.par(1, filling.type().toDoc(options)));
    }
    return Doc.vcatNonEmpty(lines);
  }
  private @NotNull Doc renderScopeVar(@NotNull PrettierOptions options, Term arg) {
    var paramDoc = freezeHoles(arg).toDoc(options);
    if (arg instanceof FreeTerm(var ref)) {
      if (ctx.contains(ref)) paramDoc = Doc.sep(paramDoc, Doc.symbol(":"), ctx.get(ref).toDoc(options));
      if (!scope.contains(ref)) paramDoc = Doc.sep(paramDoc, Doc.parened(Doc.english("not in scope")));
    }
    return Doc.par(1, paramDoc);
  }

  @Override public @NotNull SourcePos sourcePos() { return hole.ref().pos(); }
  @Override public @NotNull Severity level() { return Severity.GOAL; }
  @Override public @NotNull Stage stage() { return Stage.TYCK; }
}
