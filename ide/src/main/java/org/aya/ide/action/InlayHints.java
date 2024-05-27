// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.ide.action;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import org.aya.cli.library.source.LibrarySource;
import org.aya.ide.syntax.SyntaxNodeAction;
import org.aya.ide.util.XYXY;
import org.aya.pretty.doc.Doc;
import org.aya.syntax.concrete.Pattern;
import org.aya.syntax.core.term.Term;
import org.aya.util.error.SourcePos;
import org.aya.util.prettier.PrettierOptions;
import org.jetbrains.annotations.NotNull;

public record InlayHints(
  @NotNull PrettierOptions options,
  @NotNull XYXY location,
  @NotNull MutableList<Hint> hints
) implements SyntaxNodeAction.Ranged {
  public static @NotNull ImmutableSeq<Hint> invoke(@NotNull PrettierOptions options, @NotNull LibrarySource source, @NotNull XYXY range) {
    var program = source.program().get();
    if (program == null) return ImmutableSeq.empty();
    var maker = new InlayHints(options, range, MutableList.create());
    program.forEach(maker);
    return maker.hints.toImmutableSeq();
  }
  @Override public void visitPattern(@NotNull SourcePos pos, @NotNull Pattern pat) {
    if (pat instanceof Pattern.Bind bind && bind.type().get() instanceof Term term) {
      var type = Doc.sep(Doc.symbol(":"), term.toDoc(options));
      hints.append(new Hint(pos, type, true));
    }
    Ranged.super.visitPattern(pos, pat);
  }

  public record Hint(
    @NotNull SourcePos sourcePos,
    @NotNull Doc doc,
    boolean isType
  ) { }
}
