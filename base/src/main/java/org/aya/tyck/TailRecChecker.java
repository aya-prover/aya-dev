// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck;

import org.aya.syntax.concrete.stmt.decl.FnDecl;
import org.aya.syntax.core.def.FnDef;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.core.term.call.FnCall;
import org.aya.tyck.error.TailRecError;
import org.aya.tyck.tycker.Problematic;
import org.jetbrains.annotations.NotNull;

import java.util.function.UnaryOperator;

public interface TailRecChecker {
  class TailRecDescent implements UnaryOperator<Term> {
    private final Problematic reporter;
    private final FnDecl self;
    private boolean atTailPosition = true;

    public TailRecDescent(@NotNull Problematic reporter, @NotNull FnDecl self) {
      this.reporter = reporter;
      this.self = self;
    }

    @Override
    public Term apply(@NotNull Term term) {
      switch (term) {
        case FnCall(var ref, int ulift, var args) -> {
          if (ref instanceof FnDef.Delegate d && d.ref.equals(self.ref)) {
            if (args.size() == self.telescope.size() && !atTailPosition) {
              reporter.fail(new TailRecError(self.sourcePos()));
            }
          }
        }
        default -> {
          atTailPosition = false;
          term.descent(this);
        }
      }
      return term;
    }
  }

  static void assertTailRec(@NotNull Problematic reporter, @NotNull Term term, @NotNull FnDecl self) {
    new TailRecDescent(reporter, self).apply(term);
  }
}
