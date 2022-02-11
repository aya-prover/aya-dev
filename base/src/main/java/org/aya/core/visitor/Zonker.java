// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.visitor;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.DynamicLinkedSeq;
import kala.collection.mutable.DynamicSeq;
import kala.tuple.Unit;
import org.aya.core.sort.Sort;
import org.aya.core.term.*;
import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Style;
import org.aya.tyck.TyckState;
import org.aya.tyck.error.LevelMismatchError;
import org.aya.util.distill.DistillerOptions;
import org.aya.util.error.SourcePos;
import org.aya.util.reporter.Problem;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Instantiates holes (assuming all holes are solved).
 * Called <code>StripVisitor</code> in Arend and <code>zonk</code> in
 * GHC and Andras' setoidtt prototype. Related discussion can be found on
 * <a href="https://twitter.com/mistakenly_made/status/1382356066688651266">Twitter</a>
 * and <a href="https://stackoverflow.com/a/31890743/7083401">StackOverflow</a>.
 *
 * @author ice1000
 */
public final class Zonker implements TermFixpoint<Unit> {
  public final @NotNull TyckState state;
  public final @NotNull Reporter reporter;
  private final @NotNull DynamicLinkedSeq<Term> stack = DynamicLinkedSeq.create();
  private boolean reported = false;

  public Zonker(@NotNull TyckState state, @NotNull Reporter reporter) {
    this.state = state;
    this.reporter = reporter;
  }

  public @NotNull Term zonk(@NotNull Term term, @Nullable SourcePos pos) {
    term = term.accept(this, Unit.unit());
    var eqns = state.levelEqns().eqns();
    if (eqns.isNotEmpty() && !reported) {
      // There are level errors, but not reported since all levels are solved
      reporter.report(new LevelMismatchError(pos, eqns.toImmutableSeq()));
      eqns.clear();
    }
    return term;
  }

  @Override public void traceEntrance(@NotNull Term term, Unit unit) {
    stack.push(term);
  }

  @Override public void traceExit(@NotNull Term term) {
    stack.pop();
  }

  @Contract(pure = true) @Override public @NotNull Term visitHole(@NotNull CallTerm.Hole term, Unit unit) {
    var sol = term.ref();
    var metas = state.metas();
    if (!metas.containsKey(sol)) {
      reporter.report(new UnsolvedMeta(stack.view()
        .drop(1)
        .map(t -> t.freezeHoles(state))
        .toImmutableSeq(), sol.sourcePos, sol.name));
      return new ErrorTerm(term);
    }
    return metas.get(sol).accept(this, Unit.unit());
  }

  @Override public @NotNull Term visitMetaPat(@NotNull RefTerm.MetaPat metaPat, Unit unit) {
    return metaPat.inline();
  }

  @Override public @NotNull Sort visitSort(@NotNull Sort sort, Unit unit) {
    sort = state.levelEqns().applyTo(sort);
    var sourcePos = sort.unsolvedPos();
    if (sourcePos != null) reportLevelSolverError(sourcePos);
    return sort;
  }

  private void reportLevelSolverError(@NotNull SourcePos pos) {
    if (reported) return;
    reporter.report(new LevelMismatchError(pos, state.levelEqns().eqns().toImmutableSeq()));
    reported = true;
  }

  @Override public @NotNull Term visitUniv(FormTerm.@NotNull Univ term, Unit unit) {
    var sort = state.levelEqns().applyTo(term.sort());
    var sourcePos = sort.unsolvedPos();
    if (sourcePos != null) {
      reportLevelSolverError(sourcePos);
      return new ErrorTerm(term);
    }
    if (sort == term.sort()) return term;
    return new FormTerm.Univ(sort);
  }

  public record UnsolvedMeta(
    @NotNull ImmutableSeq<Term> termStack,
    @Override @NotNull SourcePos sourcePos, @NotNull String name
  ) implements Problem {
    @Override public @NotNull Doc describe(@NotNull DistillerOptions options) {
      var lines = DynamicSeq.of(Doc.english("Unsolved meta " + name));
      for (var term : termStack) {
        var buf = DynamicSeq.of(Doc.plain("in"), Doc.par(1, Doc.styled(Style.code(), term.toDoc(options))));
        if (term instanceof RefTerm) {
          buf.append(Doc.ALT_WS);
          buf.append(Doc.parened(Doc.english("in the type")));
        }
        lines.append(Doc.cat(buf));
      }
      return Doc.vcat(lines);
    }

    @Override public @NotNull Severity level() {
      return Severity.ERROR;
    }
  }
}
