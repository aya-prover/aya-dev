// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck;

import kala.collection.mutable.MutableStack;
import org.aya.states.InstanceSet;
import org.aya.states.TyckState;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.core.term.call.ClassCall;
import org.aya.syntax.ref.LocalCtx;
import org.aya.syntax.ref.LocalVar;
import org.aya.tyck.ctx.LocalLet;
import org.aya.tyck.tycker.AbstractTycker;
import org.aya.tyck.tycker.Unifiable;
import org.aya.unify.TermComparator;
import org.aya.unify.Unifier;
import org.aya.util.Ordering;
import org.aya.util.position.SourcePos;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/// Implements the subscoping methods for [#localCtx], [#localLet], and [#instanceSet].
public sealed abstract class ScopedTycker extends AbstractTycker implements Unifiable permits ExprTycker {
  public final @NotNull MutableStack<LocalVar> classThis = MutableStack.create();
  public @NotNull InstanceSet instanceSet;
  private @NotNull LocalLet localLet;

  protected ScopedTycker(
    @NotNull TyckState state, @NotNull InstanceSet instanceSet,
    @NotNull LocalCtx ctx, @NotNull Reporter reporter
  ) {
    super(state, ctx, reporter);
    this.localLet = new LocalLet();
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

  @Override public @NotNull TermComparator unifier(@NotNull SourcePos pos, @NotNull Ordering order) {
    return new Unifier(state(), localCtx(), reporter(), pos, order, true);
  }

  public record SubscopedVar(
    @NotNull LocalCtx parentCtx,
    @NotNull LocalVar var,
    @NotNull ScopedTycker tycker
  ) implements AutoCloseable {
    @Override public void close() {
      tycker.setLocalCtx(parentCtx);
      tycker.state.removeConnection(var);
    }
  }

  public record SubscopedAll(
    @Nullable LocalCtx parentCtx, @Nullable LocalLet parentDef,
    @Nullable InstanceSet parentInstanceSet,
    @NotNull ScopedTycker tycker
  ) implements AutoCloseable {
    @Override public void close() {
      if (parentCtx != null) {
        tycker.localCtx().extractLocal().forEach(tycker.state::removeConnection);
        tycker.setLocalCtx(parentCtx);
      }
      if (parentDef != null) tycker.setLocalLet(parentDef);
      if (parentInstanceSet != null) tycker.setInstanceSet(parentInstanceSet);
    }
  }

  public @NotNull SubscopedAll subLocalCtx() {
    return subscope(true, false, false);
  }

  public @NotNull SubscopedAll subLocalLet() {
    return subscope(false, true, false);
  }

  public @NotNull SubscopedAll subInstanceSet() {
    return subscope(false, false, true);
  }

  /// Expectation on the usage: `localCtx` being either unused or inserted a lot,
  /// and `localLet` being inserted only once.
  public @NotNull SubscopedAll subscope(
    boolean deriveLocalCtx, boolean deriveLocalLet, boolean deriveInstanceSet
  ) {
    return new SubscopedAll(
      deriveLocalCtx ? setLocalCtx(localCtx().derive()) : null,
      deriveLocalLet ? setLocalLet(localLet().derive()) : null,
      deriveInstanceSet ? setInstanceSet(instanceSet.derive()) : null,
      this
    );
  }

  public @NotNull SubscopedVar subscope(@NotNull LocalVar var, @NotNull Term type) {
    return new SubscopedVar(setLocalCtx(localCtx().derive1(var, type)), var, this);
  }

  public @NotNull LocalLet localLet() { return localLet; }
  public @NotNull LocalLet setLocalLet(@NotNull LocalLet let) {
    var old = localLet;
    this.localLet = let;
    return old;
  }
}
