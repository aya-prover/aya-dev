// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.concrete.parse;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.aya.parser.LispLexer;
import org.aya.parser.LispParser;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000
 */
public interface LispParsing {
  @Contract("_ -> new") static @NotNull LispParser parser(@NotNull String text) {
    return new LispParser(new CommonTokenStream(
      new LispLexer(CharStreams.fromString(text))));
  }
}
