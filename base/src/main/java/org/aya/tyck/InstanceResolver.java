// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck;

import kala.collection.mutable.MutableStack;
import org.aya.states.InstanceSet;
import org.aya.states.TyckState;
import org.aya.syntax.core.term.call.ClassCall;
import org.aya.syntax.ref.LocalCtx;
import org.aya.syntax.ref.LocalVar;
import org.aya.tyck.tycker.AbstractTycker;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.NotNull;

public sealed abstract class InstanceResolver extends AbstractTycker permits ExprTycker {
  public final @NotNull MutableStack<LocalVar> classThis = MutableStack.create();
  public @NotNull InstanceSet instanceSet;

  protected InstanceResolver(
    @NotNull TyckState state, @NotNull InstanceSet instanceSet,
    @NotNull LocalCtx ctx, @NotNull Reporter reporter
  ) {
    super(state, ctx, reporter);
    this.instanceSet = instanceSet;
  }

  public void pushThis(@NotNull LocalVar thisVar, @NotNull ClassCall type) {
    classThis.push(thisVar);
    instanceSet.putParam(thisVar, type);
  }

  public @NotNull LocalVar popThis() {
    var thisVar = classThis.pop();
    instanceSet.remove(thisVar);
    return thisVar;
  }

  public @NotNull InstanceSet setInstanceSet(@NotNull InstanceSet instanceSet) {
    var old = this.instanceSet;
    this.instanceSet = instanceSet;
    return old;
  }
}
