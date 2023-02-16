// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.pat;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import org.aya.concrete.Pattern;
import org.aya.ref.LocalVar;
import org.aya.util.Arg;
import org.aya.util.TreeBuilder;
import org.aya.util.error.SourcePos;
import org.aya.util.error.WithPos;
import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000, kiva
 */
public record PatTree(
  @NotNull String s,
  boolean explicit, int argsCount,
  @NotNull MutableList<PatTree> children
) implements TreeBuilder.Tree<PatTree> {
  public PatTree(@NotNull String s, boolean explicit, int argsCount) {
    this(s, explicit, argsCount, MutableList.create());
  }

  public @NotNull Arg<Pattern> toPattern() {
    var childPatterns = children.isEmpty()
      ? ImmutableSeq.<Arg<Pattern>>fill(argsCount, new Arg<>(new Pattern.Bind(SourcePos.NONE, new LocalVar("_")), true))
      : children.view().map(PatTree::toPattern).toImmutableSeq();
    var ctor = s.isEmpty()
      ? new Pattern.Tuple(SourcePos.NONE, childPatterns)
      : new Pattern.Ctor(SourcePos.NONE, new WithPos<>(SourcePos.NONE, new LocalVar(s)), childPatterns);
    return new Arg<>(ctor, explicit);
  }

  public final static class Builder extends TreeBuilder<PatTree> {
    public @NotNull PatClassifier.PatErr toPatErr() {
      return new PatClassifier.PatErr(root().map(PatTree::toPattern));
    }

    public void shiftEmpty(boolean explicit) {
      append(new PatTree("_", explicit, 0));
    }
  }
}
