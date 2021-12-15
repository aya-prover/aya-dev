// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.lsp.actions;

import kala.collection.immutable.ImmutableSeq;
import org.aya.api.distill.DistillerOptions;
import org.aya.api.ref.DefVar;
import org.aya.api.ref.LocalVar;
import org.aya.cli.library.source.LibrarySource;
import org.aya.core.def.Def;
import org.aya.lsp.utils.Resolver;
import org.aya.pretty.doc.Doc;
import org.eclipse.lsp4j.Position;
import org.jetbrains.annotations.NotNull;

public interface ComputeSignature {
  static @NotNull ImmutableSeq<Doc> invoke(
    @NotNull LibrarySource loadedFile,
    @NotNull Position position,
    boolean withResult
  ) {
    var target = Resolver.resolvePosition(loadedFile, position).firstOrNull();
    if (target == null) return ImmutableSeq.empty();
    return switch (target.data()) {
      case LocalVar localVar -> ImmutableSeq.of(Doc.plain(localVar.name()));
      case DefVar<?, ?> defVar -> computeSignature((Def) defVar.core, withResult);
      default -> ImmutableSeq.empty();
    };
  }

  static @NotNull ImmutableSeq<Doc> computeSignature(@NotNull Def core, boolean withResult) {
    var options = DistillerOptions.pretty();
    var tele = core.telescope();
    if (tele.isEmpty()) return withResult ? ImmutableSeq.of(core.result().toDoc(options)) : ImmutableSeq.empty();
    var sig = tele.view().map(it -> it.toDoc(options));
    var full = withResult
      ? sig.appended(Doc.plain(":")).appended(core.result().toDoc(options))
      : sig;
    return full.toImmutableSeq();
  }
}
