// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.visitor;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.DynamicLinkedSeq;
import kala.collection.mutable.DynamicSeq;
import org.aya.core.term.CallTerm;
import org.aya.core.term.ErrorTerm;
import org.aya.core.term.RefTerm;
import org.aya.core.term.Term;
import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Style;
import org.aya.tyck.Tycker;
import org.aya.util.distill.DistillerOptions;
import org.aya.util.error.SourcePos;
import org.aya.util.reporter.Problem;
import org.jetbrains.annotations.NotNull;

/**
 * Instantiates holes (assuming all holes are solved).
 * Called <code>StripVisitor</code> in Arend and <code>zonk</code> in
 * GHC and Andras' setoidtt prototype. Related discussion can be found on
 * <a href="https://twitter.com/mistakenly_made/status/1382356066688651266">Twitter</a>
 * and <a href="https://stackoverflow.com/a/31890743/7083401">StackOverflow</a>.
 *
 * @author ice1000
 */
public record Zonker<StackType extends DynamicLinkedSeq<Term>>(
  @NotNull @Override TermView view,
  @NotNull Tycker tycker,
  @NotNull StackType stack
) implements TermOps {
  public static @NotNull Zonker<DynamicLinkedSeq<Term>> make(@NotNull Term term, @NotNull Tycker tycker) {
    return new Zonker<>(term.view(), tycker, DynamicLinkedSeq.create());
  }

  @Override public Term pre(Term term) {
    stack.push(term);
    return switch (view.pre(term)) {
      case CallTerm.Hole hole -> {
        var sol = hole.ref();
        var metas = tycker.state.metas();
        if (!metas.containsKey(sol)) {
          tycker.reporter.report(new UnsolvedMeta(stack.view()
            .drop(1)
            .map(t -> t.freezeHoles(tycker.state))
            .toImmutableSeq(), sol.sourcePos, sol.name));
          yield new ErrorTerm(hole);
        }
        yield pre(metas.get(sol));
      }
      case RefTerm.MetaPat metaPat -> metaPat.inline();
      case Term misc -> misc;
    };
  }

  @Override public Term post(Term term) {
    stack.pop();
    return view.post(term);
  }

  @Override public @NotNull Term initial() {
    return view.initial();
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
