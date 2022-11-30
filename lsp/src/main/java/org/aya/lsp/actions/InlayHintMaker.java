// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.lsp.actions;

import kala.collection.mutable.MutableList;
import org.aya.cli.library.source.LibrarySource;
import org.aya.concrete.Pattern;
import org.aya.core.term.Term;
import org.aya.lsp.utils.LspRange;
import org.aya.lsp.utils.XYXY;
import org.aya.util.distill.DistillerOptions;
import org.javacs.lsp.InlayHint;
import org.javacs.lsp.InlayHintKind;
import org.javacs.lsp.Range;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public record InlayHintMaker(
  @NotNull XYXY location,
  @NotNull MutableList<InlayHint> hints
) implements SyntaxNodeAction.Ranged {
  public static @NotNull List<InlayHint> invoke(@NotNull LibrarySource source, @NotNull Range range) {
    var program = source.program().get();
    if (program == null) return Collections.emptyList();
    var xyxy = new XYXY(range);
    var maker = new InlayHintMaker(xyxy, MutableList.create());
    program.forEach(maker);
    return maker.hints.asJava();
  }

  @Override public @NotNull Pattern pre(@NotNull Pattern pattern) {
    if (pattern instanceof Pattern.Bind bind && bind.type().get() instanceof Term term) {
      var type = term.toDoc(DistillerOptions.pretty()).commonRender();
      var range = LspRange.toRange(bind.sourcePos());
      var hint = new InlayHint(range.end, ": " + type);
      hint.kind = InlayHintKind.Type;
      hint.paddingLeft = true;
      hints.append(hint);
    }
    return Ranged.super.pre(pattern);
  }
}
