// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.repl.command;

import org.jetbrains.annotations.NotNull;

public class DuplicateNameException extends CommandException {
  public DuplicateNameException(@NotNull String duplicateName, @NotNull Command command1, @NotNull Command command2) {
    super("Command " + command1 + " and command " + command2 + " has a duplicate name " + duplicateName);
  }
}
