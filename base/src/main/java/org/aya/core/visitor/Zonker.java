// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.visitor;

import kala.tuple.Unit;
import org.aya.api.error.Problem;
import org.aya.api.error.SourcePos;
import org.aya.core.sort.Sort;
import org.aya.core.term.CallTerm;
import org.aya.core.term.ErrorTerm;
import org.aya.core.term.FormTerm;
import org.aya.core.term.Term;
import org.aya.pretty.doc.Doc;
import org.aya.tyck.ExprTycker;
import org.aya.tyck.error.LevelMismatchError;
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
  public final @NotNull ExprTycker tycker;
  private boolean reported = false;

  /** @param tycker which stores level equation */
  public Zonker(@NotNull ExprTycker tycker) {
    this.tycker = tycker;
  }

  public boolean isReported() {
    return reported;
  }

  @Contract(pure = true) @Override public @NotNull Term visitHole(@NotNull CallTerm.Hole term, Unit unit) {
    var sol = term.ref().core();
    if (sol.body == null) {
      tycker.reporter.report(new UnsolvedMeta(sol.sourcePos));
      return new ErrorTerm(term);
    }
    return sol.body.accept(this, Unit.unit());
  }

  @Override public @Nullable Sort.CoreLevel visitLevel(@NotNull Sort.CoreLevel sort, Unit unit) {
    sort = tycker.levelEqns.applyTo(sort);
    var sourcePos = Sort.unsolvedPos(sort);
    if (sourcePos != null) {
      reportLevelSolverError(sourcePos);
      return null;
    } else return sort;
  }

  private void reportLevelSolverError(@NotNull SourcePos pos) {
    if (reported) return;
    tycker.reporter.report(new LevelMismatchError(pos, tycker.levelEqns.eqns().toImmutableSeq()));
    reported = true;
  }

  @Override public @NotNull Term visitUniv(FormTerm.@NotNull Univ term, Unit unit) {
    var sort = term.sort().subst(tycker.levelEqns);
    var sourcePos = sort.unsolvedPos();
    if (sourcePos != null) {
      reportLevelSolverError(sourcePos);
      return new ErrorTerm(term);
    }
    if (sort == term.sort()) return term;
    return new FormTerm.Univ(sort);
  }

  public static record UnsolvedMeta(@NotNull SourcePos sourcePos) implements Problem {
    @Override public @NotNull Doc describe() {
      return Doc.plain("Unsolved meta");
    }

    @Override public @NotNull Severity level() {
      return Severity.ERROR;
    }
  }
}
