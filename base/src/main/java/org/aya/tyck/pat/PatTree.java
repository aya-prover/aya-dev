// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck.pat;

import kala.collection.mutable.Buffer;
import kala.value.Ref;
import org.aya.api.error.SourcePos;
import org.aya.api.ref.LocalVar;
import org.aya.api.util.WithPos;
import org.aya.concrete.Pattern;
import org.aya.generic.GenericBuilder;
import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000, kiva
 */
public record PatTree(
  @NotNull String s,
  boolean explicit,
  @NotNull Buffer<PatTree> children
) implements GenericBuilder.Tree<PatTree> {
  public PatTree(@NotNull String s, boolean explicit) {
    this(s, explicit, Buffer.create());
  }

  public @NotNull Pattern toPattern() {
    if (children.isEmpty()) return new Pattern.Bind(SourcePos.NONE, explicit, new LocalVar(s), new Ref<>());
    return new Pattern.Ctor(SourcePos.NONE, explicit, new WithPos<>(SourcePos.NONE, s), children.view().map(PatTree::toPattern).toImmutableSeq(), null, new Ref<>(null));
  }

  public final static class Builder extends GenericBuilder<PatTree> {
    public void shiftEmpty(boolean explicit) {
      append(new PatTree("_", explicit));
    }
  }
}
