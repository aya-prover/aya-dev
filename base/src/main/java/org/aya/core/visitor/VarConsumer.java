// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.visitor;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import org.aya.core.term.*;
import org.aya.ref.LocalVar;
import org.aya.ref.Var;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.annotations.VisibleForTesting;

public interface VarConsumer extends TermConsumer {
  void var(Var var);

  @Override default void accept(@NotNull Term term) {
    switch (term) {
      case RefTerm ref -> var(ref.var());
      case RefTerm.Field field -> var(field.ref());
      case CallTerm.Hole hole -> var(hole.ref());
      case CallTerm.Fn fn -> var(fn.ref());
      case CallTerm.Prim prim -> var(prim.ref());
      case CallTerm.Data data -> var(data.ref());
      case CallTerm.Con con -> var(con.ref());
      case CallTerm.Struct struct -> var(struct.ref());
      default -> {}
    }
    TermConsumer.super.accept(term);
  }

  final class ScopeChecker implements VarConsumer {
    public final @NotNull ImmutableSeq<LocalVar> allowed;
    public final @NotNull MutableList<LocalVar> invalid;
    public final @NotNull MutableList<LocalVar> confused;
    private final @NotNull MutableList<LocalVar> bound = MutableList.create();

    @Contract(pure = true) public ScopeChecker(@NotNull ImmutableSeq<LocalVar> allowed) {
      this(allowed, MutableList.create(), MutableList.create());
    }

    @Contract(pure = true)
    private ScopeChecker(
      @NotNull ImmutableSeq<LocalVar> allowed,
      @NotNull MutableList<LocalVar> confused,
      @NotNull MutableList<LocalVar> invalid
    ) {
      this.allowed = allowed;
      this.confused = confused;
      this.invalid = invalid;
    }

    @TestOnly @VisibleForTesting public boolean isCleared() {
      return bound.isEmpty();
    }

    @Override public void accept(@NotNull Term term) {
      switch (term) {
        case IntroTerm.Lambda lambda -> {
          bound.append(lambda.param().ref());
          VarConsumer.super.accept(lambda);
          bound.removeAt(bound.size() - 1);
        }
        case FormTerm.Pi pi -> {
          bound.append(pi.param().ref());
          VarConsumer.super.accept(pi);
          bound.removeAt(bound.size() - 1);
        }
        case FormTerm.Sigma sigma -> {
          var start = bound.size();
          sigma.params().forEach(param -> {
            bound.append(param.ref());
            this.accept(param.type());
          });
          bound.removeInRange(start, start + sigma.params().size());
        }
        case CallTerm.Hole hole -> {
          var checker = new ScopeChecker(allowed.appendedAll(bound), confused, confused);
          hole.contextArgs().forEach(arg -> checker.accept(arg.term()));
          hole.args().forEach(arg -> this.accept(arg.term()));
        }
        default -> VarConsumer.super.accept(term);
      }
    }

    @Contract(mutates = "this") @Override public void var(Var v) {
      if (v instanceof LocalVar local
        && !(allowed.contains(local) || bound.contains(local))
        && !invalid.contains(local)
      ) invalid.append(local);
    }
  }

}
