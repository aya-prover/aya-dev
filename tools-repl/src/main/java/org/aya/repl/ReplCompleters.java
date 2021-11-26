// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.repl;

import org.jetbrains.annotations.NotNull;
import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;

import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

public interface ReplCompleters {
  @NotNull Completer BOOL = (reader, line, candidates) -> {
    candidates.add(new Candidate("true"));
    candidates.add(new Candidate("false"));
  };

  record EnumCompleter<T extends Enum<T>>(Class<T> enumClass) implements Completer {
    @Override public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
      Arrays.stream(enumClass.getEnumConstants()).map(Enum::name).map(Candidate::new).forEach(candidates::add);
    }
  }

  record Help(@NotNull Supplier<CommandManager> commandManager) implements Completer {
    @Override public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
      commandManager.get().cmd.view().map(CommandManager.CommandGen::owner)
        .flatMap(Command::names).map(Candidate::new).forEach(candidates::add);
    }
  }
}
