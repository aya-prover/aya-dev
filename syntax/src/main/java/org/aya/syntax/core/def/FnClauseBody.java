// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.def;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import org.aya.syntax.core.pat.Pat;
import org.aya.syntax.core.term.Term;
import org.aya.util.position.WithPos;
import org.aya.util.tyck.pat.PatClass;
import org.jetbrains.annotations.NotNull;

public final class FnClauseBody {
  public final ImmutableSeq<WithPos<Term.Matching>> clauses;
  public ImmutableSeq<PatClass.Seq<Term, Pat>> classes;
  public FnClauseBody(ImmutableSeq<WithPos<Term.Matching>> clauses) { this.clauses = clauses; }
  public @NotNull SeqView<Term.Matching> matchingsView() {
    return clauses.view().map(WithPos::data);
  }
}
