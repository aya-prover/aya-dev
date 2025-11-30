// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.normalize;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import kala.collection.mutable.MutableSinglyLinkedList;
import org.aya.generic.TermVisitor;
import org.aya.normalize.error.UnsolvedLit;
import org.aya.normalize.error.UnsolvedMeta;
import org.aya.states.TyckState;
import org.aya.syntax.compile.JitMatchy;
import org.aya.syntax.core.Closure;
import org.aya.syntax.core.annotation.Closed;
import org.aya.syntax.core.def.Matchy;
import org.aya.syntax.core.term.MetaPatTerm;
import org.aya.syntax.core.term.Param;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.core.term.call.MatchCall;
import org.aya.syntax.core.term.call.MetaCall;
import org.aya.syntax.core.term.repr.MetaLitTerm;
import org.aya.syntax.ref.MetaVar;
import org.aya.tyck.error.ClassError;
import org.aya.tyck.tycker.Problematic;
import org.aya.tyck.tycker.Stateful;
import org.aya.util.reporter.Problem;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.NotNull;

public interface Finalizer extends TermVisitor {
  @NotNull TyckState state();

  @Override @NotNull default Term term(@Closed @NotNull Term term) {
    return switch (term) {
      case MetaCall meta -> state().computeSolution(meta, this::term);
      case MetaPatTerm meta -> meta.inline(this::term);
      case MetaLitTerm meta -> meta.inline(this::term);
      case MatchCall match -> match.update(
        match.args().map(this::term),
        match.captures().map(this::term),
        switch (match.ref()) {
          case JitMatchy jit -> jit;
          case Matchy matchy -> matchy.descent(this::term);
        });
      default -> term.descent(TermVisitor.of(this::term));
    };
  }
  @Override default @NotNull Closure closure(@NotNull Closure closure) {
    return closure.reapply(this::term);
  }

  record Freeze(@NotNull Stateful delegate) implements Finalizer {
    @Override public @NotNull TyckState state() { return delegate.state(); }
    public @NotNull Term zonk(@Closed @NotNull Term term) { return term(term); }
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

    public ImmutableSeq<Param> tele(ImmutableSeq<Param> tele) {
      return tele.map(wp -> wp.descent(this::term));
    }

    public @NotNull Term term(@Closed @NotNull Term term) {
      stack.push(term);
      var result = Finalizer.super.term(term);
      // result shall not be MetaPatTerm
      switch (result) {
        case MetaCall(var ref, _) when !ref.isUser() && !alreadyReported.contains(ref) -> {
          alreadyReported.append(ref);
          Problem error;
          if (ref.req() instanceof MetaVar.OfType.ClassType clazz) {
            error = new ClassError.InstanceAmbiguous(ref.pos(),
              clazz.type(), clazz.instances());
          } else error = new UnsolvedMeta(stack.view()
            .drop(1)
            .map(this::freezeHoles)
            .toSeq(), ref.pos(), ref.name());
          fail(error);
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
