// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.repl.command;

import kala.collection.View;
import kala.collection.immutable.ImmutableSeq;
import org.aya.cli.repl.AbstractRepl;
import org.jetbrains.annotations.NotNull;

public interface Command {
  @NotNull ImmutableSeq<String> longNames();
  @NotNull ImmutableSeq<Character> shortNames();
  default @NotNull View<String> names() {
    return longNames().view().concat(shortNames().view().map(c -> Character.toString(c)));
  }

  @NotNull String description();
  default boolean hasAtLeastOneName() {
    return longNames().size() + shortNames().size() > 0;
  }

  /**
   * Execute the command.
   *
   * @param argument the command content such as args and code with the command prefix removed
   * @param repl     the REPL
   * @return the result
   */
  @NotNull CommandExecutionResult execute(@NotNull String argument, @NotNull AbstractRepl repl);
}
