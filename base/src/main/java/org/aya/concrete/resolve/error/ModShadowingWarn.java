// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.concrete.resolve.error;

import org.aya.api.error.Problem;
import org.aya.api.error.SourcePos;
import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Style;
import org.aya.util.Constants;
import kala.collection.Seq;
import org.jetbrains.annotations.NotNull;

public record ModShadowingWarn(
  @NotNull Seq<String> modName,
  @NotNull SourcePos sourcePos
) implements Problem {
  @Override public @NotNull Severity level() {
    return Severity.WARN;
  }

  @Override public @NotNull Doc describe() {
    return Doc.hsep(
      Doc.plain("The newly created module name"),
      Doc.styled(Style.code(), Doc.plain(modName.joinToString(Constants.SCOPE_SEPARATOR))),
      Doc.plain("shadows a previous definition from outer scope")
    );
  }

  @Override public @NotNull Stage stage() {
    return Stage.RESOLVE;
  }
}
