// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.lsp.actions;

import kala.collection.mutable.MutableList;
import org.aya.cli.library.source.LibrarySource;
import org.aya.concrete.Pattern;
import org.aya.lsp.utils.LspRange;
import org.aya.lsp.utils.XYXY;
import org.aya.util.distill.DistillerOptions;
import org.eclipse.lsp4j.InlayHint;
import org.eclipse.lsp4j.InlayHintKind;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public record InlayHintMaker(@NotNull MutableList<InlayHint> hints) implements SyntaxNodeAction.Ranged {
  public static @NotNull List<InlayHint> invoke(@NotNull LibrarySource source, @NotNull Range range) {
    var program = source.program().get();
    if (program == null) return Collections.emptyList();
    var xyxy = new XYXY(range);
    var maker = new InlayHintMaker(MutableList.create());
    maker.visitAll(program, xyxy);
    return maker.hints.asJava();
  }

  @Override public @NotNull Pattern visitPattern(@NotNull Pattern pattern, XYXY pp) {
    if (pattern instanceof Pattern.Bind bind && bind.type().get() != null) {
      var type = bind.type().get().toDoc(DistillerOptions.pretty()).commonRender();
      var range = LspRange.toRange(bind.sourcePos());
      var hint = new InlayHint(range.getEnd(), Either.forLeft(": " + type));
      hint.setKind(InlayHintKind.Type);
      hint.setPaddingLeft(true);
      hints.append(hint);
    }
    return Ranged.super.visitPattern(pattern, pp);
  }
}
