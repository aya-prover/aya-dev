// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.repl.jline.completer;

import kala.collection.immutable.ImmutableSeq;
import org.jetbrains.annotations.NotNull;
import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;

import java.util.List;

public final class KeywordCompleter implements Completer {
  public static final @NotNull KeywordCompleter INSTANCE = new KeywordCompleter();

  private KeywordCompleter() {
  }

  // TODO: more keywords
  private static final @NotNull ImmutableSeq<Candidate> KEYWORDS = ImmutableSeq.of(
    "open", "data", "def", "Pi", "Sig", "Type", "universe",
    "tighter", "looser", "example", "counterexample",
    "lsuc", "lmax"
  ).map(Candidate::new);

  @Override public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
    if (line.line().startsWith(":")) return;
    candidates.addAll(KEYWORDS.asJava());
  }
}
