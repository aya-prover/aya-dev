// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.states;

import kala.collection.SeqView;
import kala.collection.mutable.MutableList;
import kala.collection.mutable.MutableMap;
import org.aya.syntax.core.def.ClassDefLike;
import org.aya.syntax.core.term.FreeTerm;
import org.aya.syntax.core.term.FreeTermLike;
import org.aya.syntax.core.term.LetFreeTerm;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.core.term.call.ClassCall;
import org.aya.syntax.ref.LocalVar;
import org.aya.syntax.ref.MapLocalCtx;
import org.aya.unify.TermComparator;
import org.jetbrains.annotations.NotNull;

/// Local instance set.
/// Mutable during the typechecking of a single declaration.
///
/// @see GlobalInstanceSet
public class InstanceSet {
  public final @NotNull GlobalInstanceSet parent;
  /// The type of which is stored in a [org.aya.syntax.ref.LocalCtx]
  private final @NotNull MutableMap<ClassDefLike, MutableList<FreeTermLike>> instanceMap = MutableMap.create();
  private final @NotNull MapLocalCtx instanceTypes = new MapLocalCtx();

  public InstanceSet(@NotNull GlobalInstanceSet parent) {
    this.parent = parent;
  }

  public void putParam(@NotNull LocalVar instance, @NotNull ClassCall type) {
    instanceMap.getOrPut(type.ref(), MutableList::create).append(new FreeTerm(instance));
    instanceTypes.put(instance, type);
  }

  public void putLet(@NotNull LetFreeTerm instance, @NotNull ClassCall type) {
    instanceMap.getOrPut(type.ref(), MutableList::create).append(instance);
    instanceTypes.put(instance.name(), type);
  }

  public @NotNull SeqView<Term> find(ClassCall clazz, TermComparator comparator) {
    var local = instanceMap.getOrPut(clazz.ref(), MutableList::create);
    var global = parent.findInstanceDecls(clazz.ref());
    if (global.isEmpty() && local.isEmpty()) return SeqView.empty();
    comparator.instanceFilteringMode();
    // TODO: consider instances from `parent`
    return SeqView.narrow(local.view());
  }

  public void remove(LocalVar thisVar) {
    // Calling `getLocal` and `get` are equivalent here, since no parent
    var ty = instanceTypes.getLocal(thisVar);
    if (ty.isDefined()) {
      var clazz = ((ClassCall) ty.get()).ref();
      var list = instanceMap.getOrNull(clazz);
      if (list != null) list.removeIf(v -> v.name() == thisVar);
    }
  }
}
