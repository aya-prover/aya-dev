// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.repl.command;

import kala.collection.immutable.ImmutableSeq;
import org.jetbrains.annotations.NotNull;

public interface SingleShortNameCommand extends Command {
  @Override
  default @NotNull ImmutableSeq<Character> shortNames() {
    return ImmutableSeq.of(shortName());
  }
  char shortName();
}
