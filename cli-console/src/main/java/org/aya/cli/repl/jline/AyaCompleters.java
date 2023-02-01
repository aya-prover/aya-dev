// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.repl.jline;

import com.intellij.psi.tree.IElementType;
import kala.collection.Seq;
import kala.tuple.Tuple;
import kala.tuple.Tuple2;
import org.aya.cli.repl.AyaRepl;
import org.aya.generic.Constants;
import org.aya.parser.AyaParserDefinitionBase;
import org.jetbrains.annotations.NotNull;
import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;

import java.util.List;
import java.util.stream.Collectors;

public interface AyaCompleters {
  @NotNull List<Candidate> KEYWORDS = Seq.of(AyaParserDefinitionBase.KEYWORDS.getTypes())
    .view().map(IElementType::toString)
    .map(Candidate::new).collect(Collectors.toList());
  @NotNull Completer KW = (reader, line, candidates) -> candidates.addAll(KEYWORDS);

  class Context implements Completer {
    private final @NotNull AyaRepl repl;

    public Context(@NotNull AyaRepl repl) {
      this.repl = repl;
    }

    private @NotNull Tuple2<String, Boolean> fixWord(@NotNull String word, ParsedLine line) {
      if (word.startsWith(":") || word.startsWith(Constants.SCOPE_SEPARATOR)) {
        var idx = line.wordIndex();
        if (idx >= 1) return Tuple.of(line.words().get(idx - 1) + word, true);
      }
      return Tuple.of(word, false);
    }

    @Override public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
      var word = line.word();
      var fixed = fixWord(word, line);
      var context = repl.replCompiler.getContext();
      context.modules.view().forEach((mod, contents) -> {
        var modName = mod.ids().joinToString(Constants.SCOPE_SEPARATOR, "", Constants.SCOPE_SEPARATOR);
        if (!modName.startsWith(fixed.component1())) return;
        contents.symbols().keysView()
          .map(name -> (fixed.component2() ? Constants.SCOPE_SEPARATOR : modName) + name)
          .map(Candidate::new)
          .forEach(candidates::add);
      });
      context.symbols().keysView().map(Candidate::new).forEach(candidates::add);
    }
  }

  class Code extends Context {
    public Code(@NotNull AyaRepl repl) {
      super(repl);
    }

    @Override public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
      KW.complete(reader, line, candidates);
      super.complete(reader, line, candidates);
    }
  }
}
