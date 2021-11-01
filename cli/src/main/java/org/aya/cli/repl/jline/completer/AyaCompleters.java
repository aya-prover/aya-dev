// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.repl.jline.completer;

import kala.tuple.Tuple;
import kala.tuple.Tuple2;
import org.aya.cli.repl.Repl;
import org.aya.cli.repl.command.Command;
import org.aya.cli.repl.command.CommandManager;
import org.aya.parser.GeneratedLexerTokens;
import org.aya.util.Constants;
import org.jetbrains.annotations.NotNull;
import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;

import java.util.Arrays;
import java.util.List;

public interface AyaCompleters {
  @NotNull Completer BOOL = (reader, line, candidates) -> {
    candidates.add(new Candidate("true"));
    candidates.add(new Candidate("false"));
  };

  @NotNull List<Candidate> KEYWORDS = GeneratedLexerTokens.KEYWORDS
    .values().stream().map(Candidate::new).toList();
  @NotNull Completer KW = (reader, line, candidates) -> candidates.addAll(KEYWORDS);

  record EnumCompleter<T extends Enum<T>>(Class<T> enumClass) implements Completer {
    @Override public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
      Arrays.stream(enumClass.getEnumConstants()).map(Enum::name).map(Candidate::new).forEach(candidates::add);
    }
  }

  class Context implements Completer {
    private final @NotNull Repl repl;

    public Context(@NotNull Repl repl) {
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
        var modName = mod.joinToString(Constants.SCOPE_SEPARATOR) + Constants.SCOPE_SEPARATOR;
        if (!modName.startsWith(fixed._1)) return;
        contents.keysView()
          .map(name -> fixed._2 ? Constants.SCOPE_SEPARATOR + name : modName + name)
          .map(Candidate::new)
          .forEach(candidates::add);
      });
      context.definitions.keysView().map(Candidate::new).forEach(candidates::add);
    }
  }

  class Code extends Context {
    public Code(@NotNull Repl repl) {
      super(repl);
    }

    @Override public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
      KW.complete(reader, line, candidates);
      super.complete(reader, line, candidates);
    }
  }

  record Help(@NotNull Repl repl) implements Completer {
    @Override public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
      repl.commandManager.cmd.view().map(CommandManager.CommandGen::owner)
        .flatMap(Command::names).map(Candidate::new).forEach(candidates::add);
    }
  }
}
