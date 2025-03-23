// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.concrete.stmt.decl;

import kala.collection.immutable.ImmutableSeq;
import kala.function.CheckedConsumer;
import org.aya.syntax.ref.LocalVar;
import org.aya.util.position.WithPos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.UnaryOperator;

public class MatchBody<Clause> {
  public final @NotNull ImmutableSeq<Clause> clauses;
  public final @NotNull ImmutableSeq<WithPos<String>> rawElims;
  private @Nullable ImmutableSeq<LocalVar> elims;

  private MatchBody(
    @NotNull ImmutableSeq<Clause> clauses,
    @NotNull ImmutableSeq<WithPos<String>> rawElims,
    @Nullable ImmutableSeq<LocalVar> elims
  ) {
    this.clauses = clauses;
    this.rawElims = rawElims;
    this.elims = elims;
  }

  public MatchBody(@NotNull ImmutableSeq<Clause> clauses, @NotNull ImmutableSeq<WithPos<String>> rawElims) {
    this(clauses, rawElims, null);
  }

  public @Nullable ImmutableSeq<LocalVar> elims() {
    return elims;
  }

  public @NotNull MatchBody<Clause> update(@NotNull ImmutableSeq<Clause> clauses) {
    return this.clauses.sameElements(clauses, true)
      ? this
      : new MatchBody<>(clauses, rawElims, elims);
  }

  public void resolve(@NotNull ImmutableSeq<LocalVar> elims) {
    assert this.elims == null;
    this.elims = elims;
  }

  public @NotNull MatchBody<Clause> descent(@NotNull UnaryOperator<Clause> f) {
    return update(clauses.map(f));
  }

  public void forEach(@NotNull Consumer<Clause> f) {
    clauses.forEach(f);
  }

  public <Ex extends Throwable> void forEachChecked(@NotNull CheckedConsumer<Clause, Ex> f) throws Ex {
    clauses.forEachChecked(f);
  }
}
