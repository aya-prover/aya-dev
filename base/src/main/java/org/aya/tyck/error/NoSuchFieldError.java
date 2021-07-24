// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck.error;

import kala.collection.immutable.ImmutableSeq;
import org.aya.api.error.Problem;
import org.aya.api.error.SourcePos;
import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Style;
import org.jetbrains.annotations.NotNull;

public record NoSuchFieldError(
  @NotNull SourcePos sourcePos,
  @NotNull ImmutableSeq<String> notFound
) implements Problem {
  @Override public @NotNull Doc describe() {
    return Doc.sep(Doc.plain("No such field(s):"),
      Doc.join(Doc.plain(", "), notFound.view()
        .map(m -> Doc.styled(Style.code(), Doc.plain(m))))
    );
  }

  @Override public @NotNull Severity level() {
    return Severity.ERROR;
  }
}
