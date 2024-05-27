// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.normalize.error;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import org.aya.pretty.doc.Doc;
import org.aya.syntax.core.term.Term;
import org.aya.util.error.SourcePos;
import org.aya.util.prettier.PrettierOptions;
import org.aya.util.reporter.Problem;
import org.jetbrains.annotations.NotNull;

public record UnsolvedMeta(
  @NotNull ImmutableSeq<Term> termStack,
  @Override @NotNull SourcePos sourcePos, @NotNull String name
) implements Problem {
  @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
    var lines = MutableList.of(Doc.english("Unsolved meta " + name));
    for (var term : termStack) {
      var buf = MutableList.of(Doc.plain("in"), Doc.par(1, Doc.code(term.toDoc(options))));
      lines.append(Doc.cat(buf));
    }
    return Doc.vcat(lines);
  }

  @Override public @NotNull Severity level() { return Severity.ERROR; }
}
