// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve.error;

import org.aya.prettier.BasePrettier;
import org.aya.pretty.doc.Doc;
import org.aya.util.prettier.PrettierOptions;
import org.aya.util.reporter.Problem;
import org.aya.syntax.ref.GeneralizedVar;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;
import kala.collection.immutable.ImmutableSeq;

public record CyclicDependencyError(
  @NotNull SourcePos sourcePos,
  @NotNull GeneralizedVar var,
  @NotNull ImmutableSeq<GeneralizedVar> cyclePath
) implements Problem {
  @Override public @NotNull Severity level() { return Severity.ERROR; }
  @Override public @NotNull Stage stage() { return Stage.RESOLVE; }
  @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
    return Doc.vcat(
      Doc.english("Cyclic dependency detected in variable declarations:"),
      Doc.join(Doc.spaced(Doc.symbol("->")), cyclePath.map(BasePrettier::varDoc))
    );
  }
}
