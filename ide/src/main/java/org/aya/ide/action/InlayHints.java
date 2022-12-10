// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.ide.action;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import org.aya.cli.library.source.LibrarySource;
import org.aya.concrete.Pattern;
import org.aya.core.term.Term;
import org.aya.ide.syntax.SyntaxNodeAction;
import org.aya.ide.util.XYXY;
import org.aya.pretty.doc.Doc;
import org.aya.util.distill.DistillerOptions;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;

public record InlayHints(
  @NotNull XYXY location,
  @NotNull MutableList<Hint> hints
) implements SyntaxNodeAction.Ranged {
  public static @NotNull ImmutableSeq<Hint> invoke(@NotNull LibrarySource source, @NotNull XYXY range) {
    var program = source.program().get();
    if (program == null) return ImmutableSeq.empty();
    var maker = new InlayHints(range, MutableList.create());
    program.forEach(maker);
    return maker.hints.toImmutableSeq();
  }

  @Override public @NotNull Pattern pre(@NotNull Pattern pattern) {
    if (pattern instanceof Pattern.Bind bind && bind.type().get() instanceof Term term) {
      var type = term.toDoc(DistillerOptions.pretty());
      hints.append(new Hint(bind.sourcePos(), type, true));
    }
    return Ranged.super.pre(pattern);
  }

  public record Hint(
    @NotNull SourcePos sourcePos,
    @NotNull Doc doc,
    boolean isType
  ) {}
}
