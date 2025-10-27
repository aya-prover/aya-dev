// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.normalize;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import kala.collection.mutable.MutableSinglyLinkedList;
import org.aya.normalize.error.UnsolvedLit;
import org.aya.normalize.error.UnsolvedMeta;
import org.aya.states.TyckState;
import org.aya.syntax.compile.JitMatchy;
import org.aya.syntax.core.def.Matchy;
import org.aya.syntax.core.term.MetaPatTerm;
import org.aya.syntax.core.term.Param;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.core.term.call.MatchCall;
import org.aya.syntax.core.term.call.MetaCall;
import org.aya.syntax.core.term.repr.MetaLitTerm;
import org.aya.syntax.ref.MetaVar;
import org.aya.tyck.tycker.Problematic;
import org.aya.tyck.tycker.Stateful;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.NotNull;

public interface Finalizer {
  @NotNull TyckState state();
  default @NotNull Term doZonk(@NotNull Term term) {
    return switch (term) {
      case MetaCall meta -> state().computeSolution(meta, this::zonk);
      case MetaPatTerm meta -> meta.inline(this::zonk);
      case MetaLitTerm meta -> meta.inline(this::zonk);
      case MatchCall match -> match.update(
        match.args().map(this::zonk),
        match.captures().map(this::zonk),
        switch (match.ref()) {
          case JitMatchy jit -> jit;
          case Matchy matchy -> matchy.descent(this::zonk);
        });
      default -> term.descent(this::zonk);
    };
  }
  @NotNull Term zonk(@NotNull Term term);

  record Freeze(@NotNull Stateful delegate) implements Finalizer {
    @Override public @NotNull TyckState state() { return delegate.state(); }
    @Override public @NotNull Term zonk(@NotNull Term term) { return doZonk(term); }
  }

  /**
   * The terminology "Zonk" is borrowed from GHC,
   * see <a href="https://stackoverflow.com/a/31890743/7083401">StackOverflow</a>.
   */
  record Zonk<T extends Problematic & Stateful>(
    @NotNull T delegate, @NotNull MutableSinglyLinkedList<Term> stack,
    @NotNull MutableList<MetaVar> alreadyReported
  ) implements Finalizer, Stateful, Problematic {
    public Zonk(@NotNull T delegate) {
      this(delegate, MutableSinglyLinkedList.create(), MutableList.create());
    }
    @Override public @NotNull TyckState state() { return delegate.state(); }
    @Override public @NotNull Reporter reporter() { return delegate.reporter(); }

    public ImmutableSeq<Param> zonk(ImmutableSeq<Param> tele) {
      return tele.map(wp -> wp.descent(this::zonk));
    }

    public @NotNull Term zonk(@NotNull Term term) {
      stack.push(term);
      var result = doZonk(term);
      // result shall not be MetaPatTerm
      switch (result) {
        case MetaCall(var ref, _) when !ref.isUser() && !alreadyReported.contains(ref) -> {
          alreadyReported.append(ref);
          fail(new UnsolvedMeta(stack.view()
            .drop(1)
            .map(this::freezeHoles)
            .toSeq(), ref.pos(), ref.name()));
        }
        case MetaLitTerm mlt -> fail(new UnsolvedLit(mlt));
        default -> {
        }
      }
      stack.pop();
      return result;
    }
  }
}
