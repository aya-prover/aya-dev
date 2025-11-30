// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck;

import org.aya.syntax.concrete.stmt.decl.FnDecl;
import org.aya.syntax.core.annotation.Bound;
import org.aya.syntax.core.def.FnDef;
import org.aya.syntax.core.term.LetTerm;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.core.term.call.FnCall;
import org.aya.tyck.error.TailRecError;
import org.aya.tyck.tycker.Problematic;
import org.jetbrains.annotations.NotNull;

public interface TailRecChecker {
  class TailRecDescent {
    private final Problematic reporter;
    private final FnDecl self;

    public TailRecDescent(@NotNull Problematic reporter, @NotNull FnDecl self) {
      this.reporter = reporter;
      this.self = self;
    }

    public Term apply(@NotNull @Bound Term term, boolean tailPosition) {
      switch (term) {
        case FnCall(var ref, int ulift, var args, _) -> {
          if (ref instanceof FnDef.Delegate d && d.ref.equals(self.ref) && args.size() == self.telescope.size()) {
            if (!tailPosition) reporter.fail(new TailRecError(self.nameSourcePos()));
            return new FnCall(ref, ulift, args, true);
          }
        }
        case LetTerm l -> {
          var definedAs = l.definedAs();
          var body = l.body();

          definedAs = apply(definedAs, false);
          body = body.descent((t) -> apply(t, true));

          return l.update(definedAs, body);
        }
        default -> { }
      }

      return term.descent(t -> apply(t, false));
    }
  }

  static @NotNull Term assertTailRec(@NotNull Problematic reporter, @NotNull @Bound Term term, @NotNull FnDecl self) {
    var desc = new TailRecDescent(reporter, self);
    return desc.apply(term, true);
  }
}
