// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.def;

import kala.collection.Seq;
import kala.collection.immutable.ImmutableSeq;
import org.aya.api.error.Problem;
import org.aya.distill.CoreDistiller;
import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Docile;
import org.jetbrains.annotations.NotNull;

public sealed interface Tycked extends Docile permits Def, Tycked.Counterexample, Tycked.Example {
  record Example(@NotNull Def def) implements Tycked {
    @Override public @NotNull Doc toDoc() {
      return Doc.sep(Doc.styled(CoreDistiller.KEYWORD, "example"), def.toDoc());
    }
  }

  record Counterexample(@NotNull Def def, @NotNull ImmutableSeq<Problem> problems) implements Tycked {
    @Override public @NotNull Doc toDoc() {
      return Doc.vcat(Seq.of(
        Doc.sep(Doc.styled(CoreDistiller.KEYWORD, "counterexample"), def.toDoc())).view()
          .concat(problems.map(Problem::brief)));
    }
  }
}
