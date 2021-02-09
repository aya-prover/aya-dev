// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.concrete.resolve.error;

import org.glavo.kala.collection.Seq;
import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.jetbrains.annotations.NotNull;
import org.mzi.api.error.Problem;
import org.mzi.api.error.SourcePos;
import org.mzi.pretty.doc.Doc;

public record AmbiguousNameError(
  @NotNull String name,
  @NotNull ImmutableSeq<Seq<String>> disambiguation,
  @NotNull SourcePos sourcePos
) implements Problem.Error {
  @Override
  public @NotNull Doc describe() {
    return Doc.cat(
      Doc.plain("The unqualified name referred to by `"),
      Doc.plain(name),
      Doc.plain("` is ambiguous. "),
      Doc.plain("Try using the following module names to qualify the name to disambiguate:"),
      Doc.nest(1, Doc.vcat(disambiguation.map(a -> Doc.plain(a.joinToString("::"))).toArray(Doc.class)))
    );
  }
}
