// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck.pat;

import org.aya.generic.GenericBuilder;
import org.aya.pretty.doc.Doc;
import org.glavo.kala.collection.mutable.Buffer;
import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000, kiva
 */
public record PatTree(@NotNull String s, @NotNull Buffer<PatTree> children) implements GenericBuilder.Tree<PatTree> {
  public PatTree(@NotNull String s) {
    this(s, Buffer.create());
  }

  public @NotNull Doc toDoc() {
    return children.isEmpty()
      ? Doc.plain(s)
      : Doc.wrap("(", ")", Doc.join(Doc.plain(", "), children.stream().map(PatTree::toDoc)));
  }

  public final static class Builder extends GenericBuilder<PatTree> {
    public void shiftEmpty() {
      shift(new PatTree("_"));
      reduce();
    }
  }
}
