// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.literate;

import kala.collection.Seq;
import org.aya.pretty.doc.Doc;
import org.aya.util.error.SourcePos;
import org.aya.util.prettier.PrettierOptions;
import org.aya.util.reporter.Problem;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

public interface AttrWarn extends Problem {
  record UnknownKey(@NotNull SourcePos sourcePos, @NotNull String attr) implements AttrWarn {
    @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
      return Doc.sep(Doc.english("Unknown attribute key"), Doc.code(attr));
    }
  }
  record UnknownValue(@NotNull SourcePos sourcePos, @NotNull String value,
                      @NotNull Enum<?>[] values) implements AttrWarn {
    @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
      return Doc.vcat(
        Doc.sep(Doc.english("Unknown attribute value"), Doc.code(value), Doc.english("for the given key.")),
        Doc.sep(Doc.english("Available options (case-insensitive):"),
          Doc.commaList(Seq.from(values).map(e -> Doc.code(e.name().toLowerCase(Locale.ROOT))))
        ));
    }
  }
  @Override default @NotNull Severity level() { return Severity.WARN; }
}
