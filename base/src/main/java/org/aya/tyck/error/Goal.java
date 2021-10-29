// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.error;

import kala.collection.immutable.ImmutableSeq;
import org.aya.api.distill.DistillerOptions;
import org.aya.api.error.Problem;
import org.aya.api.error.SourcePos;
import org.aya.api.ref.LocalVar;
import org.aya.api.util.NormalizeMode;
import org.aya.core.term.CallTerm;
import org.aya.pretty.doc.Doc;
import org.aya.tyck.TyckState;
import org.jetbrains.annotations.NotNull;

public record Goal(
  @NotNull TyckState state,
  @NotNull CallTerm.Hole hole,
  @NotNull ImmutableSeq<LocalVar> scope
) implements Problem {
  @Override public @NotNull Doc describe(@NotNull DistillerOptions options) {
    var meta = hole.ref();
    var result = options.inlineMetas() ? meta.result.freezeHoles(state) : meta.result;
    var doc = Doc.vcatNonEmpty(
      Doc.english("Goal of type"),
      Doc.par(1, result.toDoc(options)),
      Doc.par(1, Doc.parened(Doc.sep(Doc.plain("Normalized:"), result.normalize(state, NormalizeMode.NF).toDoc(options)))),
      Doc.plain("Context:"),
      Doc.vcat(meta.fullTelescope().map(param -> {
        var paramDoc = param.toDoc(options);
        return Doc.par(1, scope.contains(param.ref()) ? paramDoc : Doc.sep(paramDoc, Doc.parened(Doc.english("not in scope"))));
      })),
      meta.conditions.isNotEmpty() ?
        Doc.vcat(
          ImmutableSeq.of(Doc.plain("To ensure confluence:"))
            .concat(meta.conditions.toImmutableSeq().map(tup -> Doc.par(1, Doc.cat(
              Doc.plain("Given "),
              Doc.parened(tup._1.toDoc(options)),
              Doc.plain(", we should have: "),
              tup._2.toDoc(options)
            )))))
        : Doc.empty()
    );
    var metas = state.metas();
    return !metas.containsKey(meta) ? doc :
      Doc.vcat(Doc.plain("Candidate exists:"), Doc.par(1, metas.get(meta).toDoc(options)), doc);
  }

  @Override public @NotNull SourcePos sourcePos() {
    return hole.ref().sourcePos;
  }

  @Override public @NotNull Severity level() {
    return Severity.GOAL;
  }
}
