// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.producer.flcl;

import kala.collection.immutable.ImmutableSeq;
import kala.text.StringSlice;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;

public record FlclToken(
  @NotNull SourcePos range,
  @NotNull Type type
) {

  public record File(
    @NotNull ImmutableSeq<FlclToken> tokens,
    @NotNull StringSlice sourceCode,
    int startIndex
  ) {}

  public enum Type {
    Keyword, Fn, Data, Constructor, Primitive,
    Number, Local, Comment, WhiteSpace, Eol, Symbol
  }
}
