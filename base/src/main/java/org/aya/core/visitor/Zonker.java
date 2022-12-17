// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.visitor;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import kala.collection.mutable.MutableSinglyLinkedList;
import org.aya.core.term.*;
import org.aya.pretty.doc.Doc;
import org.aya.tyck.Tycker;
import org.aya.util.prettier.PrettierOptions;
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
public record Zonker(
  @NotNull Tycker tycker,
  @NotNull MutableSinglyLinkedList<Term> stack
) implements EndoTerm {
  public static @NotNull Zonker make(@NotNull Tycker tycker) {
    return new Zonker(tycker, MutableSinglyLinkedList.create());
  }

  @Override public @NotNull Term pre(@NotNull Term term) {
    return switch (term) {
      case MetaTerm hole -> {
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
      case MetaPatTerm metaPat -> metaPat.inline(this);
      case Term misc -> misc;
    };
  }

  @Override public @NotNull Term post(@NotNull Term term) {
    return switch (term) {
      case MetaLitTerm lit -> {
        var inline = lit.inline();
        if (inline instanceof MetaLitTerm unsolved) {
          tycker.reporter.report(new UnsolvedLit(unsolved));
          yield new ErrorTerm(lit);
        }
        yield inline;
      }
      default -> EndoTerm.super.post(term);
    };
  }

  @Override public @NotNull Term apply(@NotNull Term term) {
    stack.push(term);
    var result = EndoTerm.super.apply(term);
    stack.pop();
    return result;
  }

  public record UnsolvedLit(
    @NotNull MetaLitTerm lit
  ) implements Problem {
    @Override public @NotNull SourcePos sourcePos() {
      return lit.sourcePos();
    }

    @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
      return Doc.vcat(
        Doc.english("Unable to solve the type of this literal:"),
        Doc.par(1, lit.toDoc(options)),
        Doc.plain("I'm confused about the following candidates, please help me!"),
        Doc.par(1, Doc.join(Doc.plain(", "), lit.candidates().map(d -> Doc.code(d._1.ref().name()))))
      );
    }

    @Override public @NotNull Severity level() {
      return Severity.ERROR;
    }
  }

  public record UnsolvedMeta(
    @NotNull ImmutableSeq<Term> termStack,
    @Override @NotNull SourcePos sourcePos, @NotNull String name
  ) implements Problem {
    @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
      var lines = MutableList.of(Doc.english("Unsolved meta " + name));
      for (var term : termStack) {
        var buf = MutableList.of(Doc.plain("in"), Doc.par(1, Doc.code(term.toDoc(options))));
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
