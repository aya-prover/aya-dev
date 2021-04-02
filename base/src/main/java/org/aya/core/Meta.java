// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core;

import org.aya.api.ref.CoreVar;
import org.aya.core.term.CallTerm;
import org.aya.core.term.Term;
import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.glavo.kala.collection.mutable.Buffer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class Meta {
  public final @NotNull ImmutableSeq<Term.Param> contextTele;
  public final @NotNull Buffer<Term.Param> telescope = Buffer.of();
  public @NotNull Term result;
  public @Nullable Term body;

  public ImmutableSeq<Term.Param> fullTelescope() {
    return contextTele.view().concat(telescope).toImmutableSeq();
  }

  public boolean solve(@NotNull CoreVar<Meta> v, @NotNull Term t) {
    if (t.findUsages(v) > 0) {
      return false;
    }
    assert !(t instanceof CallTerm.Hole hole) || hole.ref() == v; // [xyr]: what is this?
    body = t;
    return true;
  }

  public Meta(@NotNull ImmutableSeq<Term.Param> contextTele, @NotNull Term result) {
    this.contextTele = contextTele;
    this.result = result;
  }
}
