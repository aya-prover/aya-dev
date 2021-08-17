// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.serde;

import kala.collection.immutable.ImmutableSeq;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;

/**
 * @author ice1000
 */
public sealed interface SerDef extends Serializable {
  record QName(@NotNull ImmutableSeq<String> mod, @NotNull String name) implements Serializable {
  }

  // TODO
  record Fn(
    @NotNull ImmutableSeq<SerTerm.SerParam> telescope,
    @NotNull SerTerm result
  ) implements SerDef {
  }
}
