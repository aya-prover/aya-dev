// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.util;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableMap;
import kala.control.Option;
import kala.value.Ref;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;


/**
 * @param terminal defined when this is a terminal node
 */
public record MutableTrie<K, V>(
  @NotNull MutableMap<K, MutableTrie<K, V>> children,
  @NotNull Ref<V> terminal
) {
  public static <K, V> @NotNull MutableTrie<K, V> create() {
    return new MutableTrie<>(MutableMap.create(), new Ref<>());
  }

  public void insert(@NotNull ImmutableSeq<K> key, @NotNull V v) {
    var last = key.foldLeft(this, (trie, k) -> trie.children.getOrPut(k, MutableTrie::create));
    last.terminal.value = v;
  }

  @Contract(pure = true) public @NotNull SearchResult search(@NotNull ImmutableSeq<K> key) {
    var trie = indexOf(key);
    if (trie.isEmpty()) return SearchResult.NotFound;
    if (trie.get().terminal.value != null) return SearchResult.Found;
    return SearchResult.Partial;
  }

  @Contract(pure = true) public @NotNull Option<MutableTrie<K, V>> indexOf(@NotNull ImmutableSeq<K> key) {
    return key.foldLeft(Option.of(this), (node, k) ->
      node.mapNotNull(n -> n.children.getOrNull(k))).getOption();
  }

  public enum SearchResult {
    NotFound,
    Partial,
    Found,
  }
}
