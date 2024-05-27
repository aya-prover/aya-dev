// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.normalize;

import kala.collection.mutable.MutableSinglyLinkedList;
import org.aya.normalize.error.UnsolvedLit;
import org.aya.normalize.error.UnsolvedMeta;
import org.aya.syntax.core.term.MetaPatTerm;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.core.term.call.MetaCall;
import org.aya.syntax.core.term.repr.MetaLitTerm;
import org.aya.tyck.TyckState;
import org.aya.tyck.tycker.Problematic;
import org.aya.tyck.tycker.Stateful;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.NotNull;

public interface Finalizer {
  @NotNull TyckState state();
  default @NotNull Term doZonk(@NotNull Term term) {
    return switch (term) {
      case MetaCall meta -> state().computeSolution(meta, this::doZonk);
      case MetaPatTerm meta -> meta.inline(this::doZonk);
      case MetaLitTerm meta -> meta.inline(this::doZonk);
      default -> term.descent(this::zonk);
    };
  }
  @NotNull Term zonk(@NotNull Term term);

  record Freeze(@NotNull Stateful delegate) implements Finalizer {
    @Override public @NotNull TyckState state() { return delegate.state(); }
    @Override public @NotNull Term zonk(@NotNull Term term) { return doZonk(term); }
  }

  record Zonk<T extends Problematic & Stateful>(
    @NotNull T delegate, @NotNull MutableSinglyLinkedList<Term> stack
  ) implements Finalizer, Stateful, Problematic {
    public Zonk(@NotNull T delegate) {
      this(delegate, MutableSinglyLinkedList.create());
    }
    @Override public @NotNull TyckState state() { return delegate.state(); }
    @Override public @NotNull Reporter reporter() { return delegate.reporter(); }
    public @NotNull Term zonk(@NotNull Term term) {
      stack.push(term);
      var result = doZonk(term);
      // result shall not be MetaPatTerm
      switch (result) {
        case MetaCall meta -> fail(new UnsolvedMeta(stack.view()
          .drop(1)
          .map(this::freezeHoles)
          .toImmutableSeq(), meta.ref().pos(), meta.ref().name()));
        case MetaLitTerm mlt -> fail(new UnsolvedLit(mlt));
        default -> {
        }
      }
      stack.pop();
      return result;
    }
  }
}
