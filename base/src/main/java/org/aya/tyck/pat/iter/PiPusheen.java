// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.pat.iter;

import org.aya.generic.term.DTKind;
import org.aya.syntax.core.term.DepTypeTerm;
import org.aya.syntax.core.term.Param;
import org.aya.syntax.core.term.Term;
import org.aya.tyck.TyckState;
import org.aya.tyck.tycker.Stateful;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PiPusheen implements Pusheenable<Param, @NotNull Term>, Stateful {
  public final @NotNull TyckState state;
  private @NotNull Term body;
  private @Nullable Term whnfBody;
  private @Nullable Param peek;
  int pusheenCount = 0;

  public PiPusheen(@NotNull TyckState state, @NotNull Term body) {
    this.body = body;
    this.state = state;
  }

  public @NotNull TyckState state() {
    return state;
  }

  private @NotNull Term whnfBody() {
    if (whnfBody != null) return whnfBody;
    return whnf(body);
  }

  @Override
  public @NotNull Param peek() {
    if (peek != null) return peek;

    var body = whnfBody();
    assert body instanceof DepTypeTerm maybePi && maybePi.kind() == DTKind.Pi;
    var pusheenCount = this.pusheenCount++;

    return peek = new Param(Integer.toString(pusheenCount), ((DepTypeTerm) body).param(), true);
  }

  @Override
  public @NotNull Term body() {
    return body;
  }

  @Override
  public boolean hasNext() {
    return peek != null || (whnfBody() instanceof DepTypeTerm maybePi && maybePi.kind() == DTKind.Pi);
  }

  @Override
  public @NotNull Param next() {
    if (peek == null) peek();
    var result = peek;

    // update body
    var body = whnfBody();
    assert body instanceof DepTypeTerm maybePi && maybePi.kind() == DTKind.Pi;
    this.body = ((DepTypeTerm) body).body().toLocns().body();
    this.whnfBody = null;
    this.peek = null;

    return result;
  }
}
