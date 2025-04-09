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
        case FnCall(var ref, int ulift, var args, _) -> {
          if (ref instanceof FnDef.Delegate d && d.ref.equals(self.ref) && args.size() == self.telescope.size()) {
            if (!atTailPosition) reporter.fail(new TailRecError(self.sourcePos()));
            return new FnCall(ref, ulift, args, true);
          }
        }
        default -> { }
      }
      atTailPosition = false;
      return term.descent(this);
    }
  }

  static @NotNull Term assertTailRec(@NotNull Problematic reporter, @NotNull Term term, @NotNull FnDecl self) {
    var desc = new TailRecDescent(reporter, self);
    return desc.apply(term);
  }
}
