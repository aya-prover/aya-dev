// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck;

import org.aya.core.term.Term;
import org.aya.core.visitor.Zonker;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.NotNull;

public abstract class Tycker {
  public final @NotNull Reporter reporter;
  public final @NotNull TyckState state;

  protected Tycker(@NotNull Reporter reporter, @NotNull TyckState state) {
    this.reporter = reporter;
    this.state = state;
  }

  public @NotNull Term zonk(@NotNull Term term) {
    return Zonker.make(this).apply(term);
  }
}
