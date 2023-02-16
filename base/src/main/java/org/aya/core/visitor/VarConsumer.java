// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.visitor;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import org.aya.core.term.*;
import org.aya.ref.AnyVar;
import org.aya.ref.LocalVar;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.annotations.VisibleForTesting;

// TODO: Also visit variables in patterns?
public interface VarConsumer extends TermConsumer {
  void var(@NotNull AnyVar var);

  @Override default void accept(@NotNull Term term) {
    switch (term) {
      case RefTerm ref -> var(ref.var());
      case RefTerm.Field field -> var(field.ref());
      case MetaTerm hole -> var(hole.ref());
      case FnCall fn -> var(fn.ref());
      case PrimCall prim -> var(prim.ref());
      case DataCall data -> var(data.ref());
      case ConCall con -> var(con.ref());
      case ClassCall struct -> var(struct.ref());
      default -> {}
    }
    TermConsumer.super.accept(term);
  }

  abstract class Scoped implements VarConsumer {
    protected final @NotNull MutableList<LocalVar> bound = MutableList.create();

    @TestOnly @VisibleForTesting public boolean isCleared() {
      return bound.isEmpty();
    }

    @Override public void accept(@NotNull Term term) {
      switch (term) {
        case LamTerm lambda -> {
          bound.append(lambda.param().ref());
          VarConsumer.super.accept(lambda);
          bound.removeLast();
        }
        case PiTerm pi -> {
          bound.append(pi.param().ref());
          VarConsumer.super.accept(pi);
          bound.removeLast();
        }
        case SigmaTerm(var params) -> {
          var start = bound.size();
          params.forEach(param -> {
            bound.append(param.ref());
            accept(param.type());
          });
          bound.removeInRange(start, start + params.size());
        }
        case PathTerm cube -> {
          var start = bound.size();
          cube.params().forEach(bound::append);
          accept(cube.type());
          cube.partial().termsView().forEach(this);
          bound.removeInRange(start, start + cube.params().size());
        }
        case PLamTerm(var params, var body) -> {
          var start = bound.size();
          params.forEach(bound::append);
          accept(body);
          bound.removeInRange(start, start + params.size());
        }
        default -> VarConsumer.super.accept(term);
      }
    }
  }

  final class ScopeChecker extends Scoped {
    public final @NotNull ImmutableSeq<LocalVar> allowed;
    public final @NotNull MutableList<LocalVar> invalid;
    public final @NotNull MutableList<LocalVar> confused;

    @Contract(pure = true) public ScopeChecker(@NotNull ImmutableSeq<LocalVar> allowed) {
      this(allowed, MutableList.create(), MutableList.create());
    }

    @Override public void accept(@NotNull Term term) {
      if (term instanceof MetaTerm hole) {
        var checker = new ScopeChecker(allowed.appendedAll(bound), confused, confused);
        hole.contextArgs().forEach(arg -> checker.accept(arg.term()));
        hole.args().forEach(arg -> accept(arg.term()));
      } else super.accept(term);
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

    @Contract(mutates = "this") @Override public void var(@NotNull AnyVar v) {
      if (v instanceof LocalVar local
        && !(allowed.contains(local) || bound.contains(local))
        && !invalid.contains(local)
      ) invalid.append(local);
    }
  }

}
