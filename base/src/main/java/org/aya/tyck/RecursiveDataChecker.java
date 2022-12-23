// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck;

import kala.collection.immutable.ImmutableSeq;
import org.aya.concrete.stmt.TeleDecl;
import org.aya.core.def.DataDef;
import org.aya.core.term.Term;
import org.aya.ref.DefVar;
import org.aya.tyck.error.NonPositiveDataError;
import org.aya.tyck.tycker.TyckState;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.NotNull;

public final class RecursiveDataChecker extends CovarianceChecker {
  private final @NotNull ImmutableSeq<DefVar<DataDef, TeleDecl.DataDecl>> refs;
  private final @NotNull TeleDecl.DataCtor ctor;
  private final @NotNull Reporter reporter;

  @Override
  protected boolean checkNonCovariant(@NotNull Term term) {
    for (var ref : refs) {
      if (term.findUsages(ref) > 0) {
        reporter.report(new NonPositiveDataError(ctor.sourcePos(), ctor));
        return true;
      }
    }
    return false;
  }

  public RecursiveDataChecker(@NotNull TyckState state, @NotNull Reporter reporter, ImmutableSeq<DefVar<DataDef, TeleDecl.DataDecl>> refs, TeleDecl.@NotNull DataCtor ctor) {
    super(state);
    this.refs = refs;
    this.reporter = reporter;
    this.ctor = ctor;
  }

  public RecursiveDataChecker(@NotNull TyckState state, @NotNull Reporter reporter, DefVar<DataDef, TeleDecl.DataDecl> ref, TeleDecl.@NotNull DataCtor ctor) {
    super(state);
    this.refs = ImmutableSeq.of(ref);
    this.reporter = reporter;
    this.ctor = ctor;
  }
}
