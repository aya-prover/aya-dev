// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.ref;

import kala.collection.SeqView;
import kala.collection.mutable.MutableLinkedHashMap;
import kala.collection.mutable.MutableList;
import kala.control.Option;
import org.aya.syntax.core.term.Term;
import org.aya.util.Scoped;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.UnaryOperator;

public record LocalCtx(
  @NotNull MutableLinkedHashMap<LocalVar, Term> binds,
  @NotNull MutableList<LocalVar> vars,
  @Override @Nullable LocalCtx parent
) implements Scoped<LocalVar, Term, LocalCtx> {
  public LocalCtx() {
    this(MutableLinkedHashMap.of(), MutableList.create(), null);
  }

  public boolean isEmpty() {
    return binds.isEmpty() && (parent == null || parent.isEmpty());
  }

  @Override public @NotNull LocalCtx self() { return this; }

  @Override @Contract("-> new")
  public @NotNull LocalCtx derive() {
    return new LocalCtx(MutableLinkedHashMap.of(), MutableList.create(), this);
  }

  public int size() {
    return binds.size() + (parent == null ? 0 : parent.size());
  }

  public @NotNull Option<Term> getLocal(@NotNull LocalVar name) {
    return binds.getOption(name);
  }

  @Override public void putLocal(@NotNull LocalVar key, @NotNull Term value) {
    binds.put(key, value);
    vars.append(key);
  }

  @Contract(value = "_ -> new", pure = true)
  public @NotNull LocalCtx map(UnaryOperator<Term> mapper) {
    var newBinds = binds.view()
      .mapValues((_, t) -> mapper.apply(t));

    return new LocalCtx(MutableLinkedHashMap.from(newBinds), vars, parent == null ? null : parent.map(mapper));
  }

  /**
   * Collect free variables of this context, usually used for {@link MetaVar}
   */
  public @NotNull SeqView<LocalVar> extract() {
    SeqView<LocalVar> parentView = parent == null ? SeqView.empty() : parent.extract();
    return parentView.concat(vars);
  }

  @SuppressWarnings("MethodDoesntCallSuperMethod")
  @Override public @NotNull LocalCtx clone() {
    return new LocalCtx(binds.clone(), vars.clone(), parent != null ? parent.clone() : null);
  }
}
