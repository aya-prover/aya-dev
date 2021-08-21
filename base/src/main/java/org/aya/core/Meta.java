// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.Buffer;
import org.aya.api.error.SourcePos;
import org.aya.api.ref.HoleVar;
import org.aya.core.term.FormTerm;
import org.aya.core.term.Term;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author ice1000, re-xyr
 */
public final class Meta {
  public final @NotNull ImmutableSeq<Term.Param> contextTele;
  public final @NotNull ImmutableSeq<Term.Param> telescope;
  public final @NotNull Term result;
  public final @NotNull SourcePos sourcePos;
  public @Nullable Term body;

  public SeqView<Term.Param> fullTelescope() {
    return contextTele.view().concat(telescope);
  }

  public boolean solve(@NotNull HoleVar<Meta> v, @NotNull Term t) {
    if (t.findUsages(v) > 0) return false;
    body = t;
    return true;
  }

  private Meta(
    @NotNull ImmutableSeq<Term.Param> contextTele,
    @NotNull ImmutableSeq<Term.Param> telescope,
    @NotNull Term result, @NotNull SourcePos sourcePos
  ) {
    this.contextTele = contextTele;
    this.telescope = telescope;
    this.result = result;
    this.sourcePos = sourcePos;
  }

  public static @NotNull Meta from(
    @NotNull ImmutableSeq<Term.Param> contextTele,
    @NotNull Term result, @NotNull SourcePos sourcePos
  ) {
    if (result instanceof FormTerm.Pi pi) {
      var buf = Buffer.<Term.Param>create();
      var r = pi.parameters(buf);
      return new Meta(contextTele, buf.toImmutableSeq(), r, sourcePos);
    } else return new Meta(contextTele, ImmutableSeq.empty(), result, sourcePos);
  }
}
