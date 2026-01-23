// Copyright (c) 2020-2026 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.library.source;

import kala.collection.immutable.ImmutableSet;
import kala.collection.mutable.MutableSet;
import kala.collection.mutable.MutableStack;
import kala.tuple.primitive.IntObjTuple2;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

@FunctionalInterface
public interface LibraryVisitor {
  enum VisitResult {
    Break,
    Continue,
    Recursive
  }

  // C-style, sorry
  @NotNull VisitResult visit(@NotNull LibraryOwner owner, @Nullable LibraryOwner parent);

  static @NotNull ImmutableSet<LibraryOwner> visit(@NotNull LibraryOwner owner, @NotNull LibraryVisitor visitor) {
    // i don't know... i just don't want to write a sealed interface
    var visited = MutableSet.<LibraryOwner>create();
    var queue = MutableStack.<IntObjTuple2<@Nullable LibraryOwner>>create();
    @Nullable LibraryOwner parent = null;
    queue.push(IntObjTuple2.of(0, owner));

    outer:
    while (queue.isNotEmpty()) {
      var frame = queue.pop();
      var node = frame.component2();    // node is only null when isReturn is true
      var isReturn = frame.component1() != 0;    // 0 for normal node, others for return node
      if (isReturn) {
        parent = node;
        continue;
      }

      Objects.requireNonNull(node);
      if (visited.contains(node)) continue;
      var result = visitor.visit(node, parent);
      visited.add(node);

      switch (result) {
        case Break -> { break outer; }
        case Continue -> { continue; }
        case Recursive -> {
          queue.push(IntObjTuple2.of(1, parent));
          parent = node;
          node.libraryDeps().reversed().forEach(dep -> queue.push(IntObjTuple2.of(0, dep)));
        }
      }
    }

    return visited.toSet();
  }
}
