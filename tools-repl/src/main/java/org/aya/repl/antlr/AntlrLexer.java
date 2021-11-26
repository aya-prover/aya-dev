// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.repl.antlr;

import kala.collection.SeqView;
import org.antlr.v4.runtime.Token;
import org.jetbrains.annotations.NotNull;

@FunctionalInterface
public interface AntlrLexer {
  /** @return parsed tokens without the last EOF token */
  @NotNull SeqView<Token> tokensNoEOF(String line);
}
