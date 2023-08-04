// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.literate;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import org.jetbrains.annotations.MustBeInvokedByOverriders;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

public interface LiterateConsumer extends Consumer<Literate> {
  @MustBeInvokedByOverriders
  default void accept(@NotNull Literate literate) {
    switch (literate) {
      case Literate.Many many -> many.children().forEach(this);
      case Literate.List items -> items.items().forEach(this);
      case Literate.Unsupported(var children) -> children.forEach(this);
      default -> {}
    }
  }

  interface LiterateExtractinator<T> extends LiterateConsumer {
    @NotNull MutableList<T> result();

    default @NotNull ImmutableSeq<T> extract(Literate literate) {
      accept(literate);
      return result().toImmutableSeq();
    }
  }

  record InstanceExtractinator<T extends Literate>(
    @NotNull MutableList<T> result, @NotNull Class<T> clazz
  ) implements LiterateExtractinator<T> {

    public InstanceExtractinator(@NotNull Class<T> clazz) {
      this(MutableList.create(), clazz);
    }

    @Override public void accept(@NotNull Literate literate) {
      if (clazz.isInstance(literate)) result.append(clazz.cast(literate));
      LiterateExtractinator.super.accept(literate);
    }
  }
}
