// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.repl.command;

import kala.collection.immutable.ImmutableSeq;
import org.jetbrains.annotations.NotNull;

public interface SingleLongNameCommand extends Command {
  @Override
  default @NotNull ImmutableSeq<String> longNames() {
    return ImmutableSeq.of(longName());
  }

  @NotNull String longName();
}
