// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.repl.jline.completer;

import org.aya.cli.repl.command.DefaultCommandUtils;
import org.jetbrains.annotations.NotNull;

public final class DefaultCommandCompleter extends CommandCompleter {
  public static final @NotNull DefaultCommandCompleter INSTANCE = new DefaultCommandCompleter();

  private DefaultCommandCompleter() {
    super(DefaultCommandUtils.defaultCommands());
  }
}
