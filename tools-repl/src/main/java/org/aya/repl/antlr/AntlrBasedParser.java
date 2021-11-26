// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.repl.antlr;

import org.jetbrains.annotations.NotNull;
import org.jline.reader.CompletingParsedLine;

import java.util.List;

public abstract class AntlrBasedParser {
  public record ReplParsedLine(
    @Override int wordCursor,
    @Override @NotNull List<@NotNull String> words,
    @Override @NotNull String word,
    @Override int wordIndex,
    @Override @NotNull String line,
    @Override int cursor
  ) implements CompletingParsedLine {
    @Override public CharSequence escape(CharSequence charSequence, boolean b) {
      return charSequence;
    }

    @Override public int rawWordCursor() {
      return wordCursor;
    }

    @Override public int rawWordLength() {
      return word.length();
    }
  }
}
