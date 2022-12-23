// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.covariance;

import org.aya.concrete.stmt.TeleDecl;
import org.aya.core.term.Term;
import org.aya.tyck.covariance.CovarianceChecker;
import org.aya.tyck.error.NonPositiveDataError;
import org.aya.tyck.tycker.TyckState;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.NotNull;

public final class RecursiveDataChecker extends CovarianceChecker {
  private final @NotNull TeleDecl.DataCtor ctor;
  private final @NotNull Reporter reporter;

  @Override
  protected boolean checkNonCovariant(@NotNull Term term) {
    if (term.findUsages(ctor.dataRef) > 0) {
      reporter.report(new NonPositiveDataError(ctor.sourcePos(), ctor, term));
      return true;
    }
    return false;
  }

  public RecursiveDataChecker(@NotNull TyckState state, @NotNull Reporter reporter, TeleDecl.@NotNull DataCtor ctor) {
    super(state);
    this.reporter = reporter;
    this.ctor = ctor;
  }
}
