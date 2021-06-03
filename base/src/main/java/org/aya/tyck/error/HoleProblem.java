// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck.error;

import org.aya.api.error.Problem;
import org.aya.api.error.SourcePos;
import org.aya.api.ref.LocalVar;
import org.aya.core.term.CallTerm;
import org.aya.core.term.Term;
import org.aya.core.visitor.CoreDistiller;
import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Docile;
import org.aya.pretty.doc.Style;
import kala.collection.Seq;
import org.jetbrains.annotations.NotNull;

/** @author ice1000 */
public sealed interface HoleProblem extends Problem {
  @NotNull CallTerm.Hole term();

  @Override default @NotNull Severity level() {
    return Severity.ERROR;
  }

  /** @author ice1000 */
  record BadSpineError(
    @NotNull CallTerm.Hole term,
    @NotNull SourcePos sourcePos
  ) implements HoleProblem {
    @Override public @NotNull Doc describe() {
      return Doc.vcat(
        Doc.plain("Can't perform pattern unification on hole with the following spine:"),
        Doc.join(Doc.symbol(", "), term.args().map(Docile::toDoc))
      );
    }
  }

  record BadlyScopedError(
    @NotNull CallTerm.Hole term,
    @NotNull Term solved,
    @NotNull Seq<LocalVar> scopeCheck,
    @NotNull SourcePos sourcePos
  ) implements HoleProblem {
    @Override public @NotNull Doc describe() {
      return Doc.vcat(
        Doc.hsep(Doc.plain("The solution"), Doc.styled(Style.code(), solved.toDoc()), Doc.plain("is not well-scoped")),
        Doc.hcat(Doc.plain("In particular, these variables are not in scope: "),
          Doc.join(Doc.symbol(", "), scopeCheck.stream()
            .map(CoreDistiller::varDoc)
            .map(doc -> Doc.styled(Style.code(), doc)))));
    }
  }

  /**
   * @author ice1000
   */
  record RecursionError(
    @NotNull CallTerm.Hole term,
    @NotNull Term sol,
    @NotNull SourcePos sourcePos
  ) implements HoleProblem {
    @Override public @NotNull Doc describe() {
      return Doc.hsep(
        Doc.plain("Trying to solve hole"),
        Doc.styled(Style.code(), CoreDistiller.plainLinkDef(term.ref())),
        Doc.plain("as"),
        Doc.styled(Style.code(), sol.toDoc()),
        Doc.plain("which is recursive"));
    }
  }
}
