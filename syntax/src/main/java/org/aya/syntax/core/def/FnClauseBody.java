// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.def;

import kala.collection.immutable.ImmutableSeq;
import org.aya.syntax.core.term.Term;
import org.aya.util.error.WithPos;

public final class FnClauseBody {
  public final ImmutableSeq<WithPos<Term.Matching>> clauses;
  public FnClauseBody(ImmutableSeq<WithPos<Term.Matching>> clauses) { this.clauses = clauses; }
}
