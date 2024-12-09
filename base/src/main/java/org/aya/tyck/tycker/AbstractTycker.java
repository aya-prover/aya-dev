// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.tycker;

import kala.value.LazyValue;
import org.aya.generic.Renamer;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.ref.LocalCtx;
import org.aya.syntax.ref.LocalVar;
import org.aya.tyck.ExprTycker;
import org.aya.tyck.Jdg;
import org.aya.tyck.TyckState;
import org.aya.unify.Synthesizer;
import org.aya.unify.TermComparator;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

public sealed abstract class AbstractTycker implements Stateful, Contextful, Problematic permits ExprTycker, TermComparator {
  public final @NotNull TyckState state;
  private @NotNull LocalCtx localCtx;
  public final @NotNull Reporter reporter;

  protected AbstractTycker(@NotNull TyckState state, @NotNull LocalCtx ctx, @NotNull Reporter reporter) {
    this.state = state;
    this.localCtx = ctx;
    this.reporter = reporter;
  }

  @Override public @NotNull LocalCtx setLocalCtx(@NotNull LocalCtx ctx) {
    var old = this.localCtx;
    this.localCtx = ctx;
    return old;
  }

  @Override public @NotNull LocalCtx localCtx() { return localCtx; }
  @Override public @NotNull TyckState state() { return state; }
  @Override public @NotNull Reporter reporter() { return reporter; }

  public @NotNull Jdg.Lazy lazyJdg(@NotNull Term wellTyped) {
    return new Jdg.Lazy(wellTyped, LazyValue.of(() ->
      new Synthesizer(this).synthDontNormalize(wellTyped)));
  }

  public @NotNull SubscopedVar subscope(@NotNull Term type, @NotNull Renamer nameGen) {
    return new SubscopedVar(type, nameGen);
  }

  public final class SubscopedVar implements AutoCloseable {
    private final @NotNull LocalCtx parentCtx;
    private final @NotNull LocalVar var;
    private final @NotNull Renamer nameGen;

    public SubscopedVar(@NotNull Term type, @NotNull Renamer nameGen) {
      this.nameGen = nameGen;
      this.var = nameGen.bindName(type);
      this.parentCtx = setLocalCtx(localCtx.derive1(var, type));
    }

    public @NotNull LocalVar var() {
      return var;
    }

    @Override
    public void close() {
      setLocalCtx(parentCtx);
      nameGen.unbindName(var);
      state.removeConnection(var);
    }
  }
}
