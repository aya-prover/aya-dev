// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.repl.jline.completer;

import kala.tuple.Tuple;
import kala.tuple.Tuple2;
import org.aya.cli.repl.ReplContext;
import org.aya.cli.repl.command.Command;
import org.aya.cli.repl.command.CommandManager;
import org.aya.parser.GeneratedLexerTokens;
import org.aya.util.Constants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;

import java.util.List;

public interface ArgCompleter extends Completer {
  @NotNull CommandManager manager();
  @NotNull List<Candidate> KEYWORDS = GeneratedLexerTokens.KEYWORDS
    .values().stream().map(Candidate::new).toList();

  record Keywords(@NotNull CommandManager manager) implements ArgCompleter {
    @Override public void complete(@Nullable Command command, ParsedLine line, List<Candidate> candidates) {
      if (command == null) candidates.addAll(KEYWORDS);
    }
  }

  record Strings(@NotNull CommandManager manager) implements ArgCompleter {
    @Override public void complete(@Nullable Command command, ParsedLine line, List<Candidate> candidates) {
      if (command instanceof Command.StringCommand stringCommand)
        stringCommand.params().forEach(candidates::add);
    }
  }

  record Symbols(@NotNull CommandManager manager, @NotNull ReplContext context) implements ArgCompleter {
    private @NotNull Tuple2<String, Boolean> fixWord(@NotNull String word, ParsedLine line) {
      if (word.startsWith(":") || word.startsWith(Constants.SCOPE_SEPARATOR)) {
        var idx = line.wordIndex();
        if (idx >= 1) return Tuple.of(line.words().get(idx - 1) + word, true);
      }
      return Tuple.of(word, false);
    }

    @Override public void complete(@Nullable Command command, ParsedLine line, List<Candidate> candidates) {
      if (command == null || command instanceof Command.CodeCommand) {
        var word = line.word();
        var fixed = fixWord(word, line);
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
  }

  /** @param command null if no command (so the input should be Aya code) */
  void complete(@Nullable Command command, ParsedLine line, List<Candidate> candidates);
  @Override default void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
    var trim = line.line().trim();
    if (trim.startsWith(Command.PREFIX)) {
      var life = manager().parse(trim.substring(1)).command();
      if (life.isDefined()) complete(life.get(), line, candidates);
    } else complete((Command) null, line, candidates);
  }
}
