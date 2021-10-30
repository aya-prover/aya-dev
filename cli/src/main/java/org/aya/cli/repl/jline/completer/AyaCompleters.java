// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.repl.jline.completer;

import org.aya.cli.repl.ReplContext;
import org.aya.parser.GeneratedLexerTokens;
import org.aya.util.Constants;
import org.jetbrains.annotations.NotNull;
import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;

import java.util.Arrays;
import java.util.List;

public class AyaCompleters {
  public static class BooleanCompleter implements Completer {
    public static final BooleanCompleter INSTANCE = new BooleanCompleter();

    @Override public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
      candidates.add(new Candidate("true"));
      candidates.add(new Candidate("false"));
    }
  }

  public static class KwCompleter implements Completer {
    public static final KwCompleter INSTANCE = new KwCompleter();
    private static final @NotNull List<Candidate> KEYWORDS = GeneratedLexerTokens.KEYWORDS
      .values().stream().map(Candidate::new).toList();

    @Override public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
      candidates.addAll(KEYWORDS);
    }
  }

  public static record EnumCompleter<T extends Enum<T>>(Class<T> enumClass) implements Completer {
    @Override public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
      Arrays.stream(enumClass.getEnumConstants()).map(Enum::name).map(Candidate::new).forEach(candidates::add);
    }
  }

  public static class ContextCompleter implements Completer {
    private final @NotNull ReplContext context;

    public ContextCompleter(@NotNull ReplContext context) {
      this.context = context;
    }

    @Override public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
      var word = line.word();
      context.modules.view().forEach((mod, contents) -> {
        var modName = mod.joinToString(Constants.SCOPE_SEPARATOR) + Constants.SCOPE_SEPARATOR;
        if (!modName.startsWith(word)) return;
        contents.keysView()
          .map(name -> modName + name)
          .map(Candidate::new)
          .forEach(candidates::add);
      });
      context.definitions.keysView().map(Candidate::new).forEach(candidates::add);
    }
  }

  public static class CodeCompleter extends ContextCompleter {
    public CodeCompleter(@NotNull ReplContext context) {
      super(context);
    }

    @Override public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
      AyaCompleters.KwCompleter.INSTANCE.complete(reader, line, candidates);
      super.complete(reader, line, candidates);
    }
  }
}
