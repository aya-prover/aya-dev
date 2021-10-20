// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.repl.command;

import kala.collection.immutable.ImmutableSeq;

public final class DefaultCommandUtils {
  private DefaultCommandUtils() {
  }

  public static ImmutableSeq<Command> defaultCommands() {
    return ImmutableSeq.of(QuitCommand.INSTANCE, PromptCommand.INSTANCE);
  }
}
