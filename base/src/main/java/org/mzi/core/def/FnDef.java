// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.core.def;

import org.jetbrains.annotations.NotNull;
import org.mzi.core.term.Term;
import org.mzi.generic.Tele;
import org.mzi.ref.DefVar;

/**
 * @author ice1000
 */
public final class FnDef implements Def {
  public final @NotNull DefVar<Def> ref;
  public final @NotNull Tele<Term> telescope;
  public final @NotNull Term result;
  public final @NotNull Term body;

  public FnDef(@NotNull String name, @NotNull Tele<Term> telescope, @NotNull Term result, @NotNull Term body) {
    this.ref = new DefVar<>(this, name);
    this.telescope = telescope;
    this.result = result;
    this.body = body;
  }

  @Override public @NotNull DefVar<Def> ref() {
    return ref;
  }
}
