// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.states;

import kala.collection.Seq;
import kala.collection.SeqView;
import kala.collection.mutable.MutableList;
import kala.collection.mutable.MutableMap;
import kala.control.Option;
import org.aya.syntax.core.def.ClassDefLike;
import org.aya.syntax.core.term.FreeTerm;
import org.aya.syntax.core.term.FreeTermLike;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.core.term.call.ClassCall;
import org.aya.syntax.ref.LocalVar;
import org.aya.syntax.ref.MapLocalCtx;
import org.aya.unify.TermComparator;
import org.aya.util.Scoped;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/// Local instance set.
/// Mutable during the typechecking of a single declaration.
///
/// @see GlobalInstanceSet
/// @param root shared in the entire chain of instance sets, so `this.root == this.parent.root`
public record InstanceSet(
  @NotNull GlobalInstanceSet root,
  @Override @Nullable InstanceSet parent,
  @NotNull MutableMap<ClassDefLike, MutableList<FreeTermLike>> instanceMap,
  @NotNull MapLocalCtx instanceTypes
) implements Scoped<FreeTermLike, ClassCall, InstanceSet> {
  public InstanceSet(@NotNull GlobalInstanceSet root, @Nullable InstanceSet parent) {
    this(root, parent, MutableMap.create(), new MapLocalCtx());
  }
  public InstanceSet(@NotNull GlobalInstanceSet root) {
    this(root, null);
  }
  @Override public @NotNull InstanceSet self() { return this; }
  @Override public @NotNull InstanceSet derive() { return new InstanceSet(root, this); }
  @Override public @NotNull Option<ClassCall> getLocal(@NotNull FreeTermLike key) {
    return instanceTypes.getLocal(key.name()).map(it -> (ClassCall) it);
  }
  @Override public void putLocal(@NotNull FreeTermLike instance, @NotNull ClassCall type) {
    instanceMap.getOrPut(type.ref(), MutableList::create).append(instance);
    instanceTypes.put(instance.name(), type);
  }

  public void putParam(@NotNull LocalVar instance, @NotNull ClassCall type) { put(new FreeTerm(instance), type); }

  public @NotNull SeqView<Term> find(ClassCall clazz, TermComparator comparator) {
    Seq<FreeTermLike> local = findFirst(inst -> inst.instanceMap.getOrNull(clazz.ref()));
    if (local == null) local = Seq.empty();
    var global = root.findInstanceDecls(clazz.ref());
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
