// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.value;

import kala.collection.immutable.ImmutableSeq;
import kala.value.LazyValue;
import org.aya.api.ref.LocalVar;
import org.aya.api.ref.Var;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

public sealed interface RefValue extends Value {
  record Neu(LocalVar var, ImmutableSeq<Segment> spine) implements RefValue {
    public Neu(LocalVar var) {
      this(var, ImmutableSeq.empty());
    }

    @Contract("_ -> new") @Override
    public @NotNull Neu apply(Arg arg) {
      return new Neu(var, spine.appended(new Segment.Apply(arg)));
    }

    @Contract(" -> new") @Override
    public @NotNull Neu projL() {
      return new Neu(var, spine.appended(new Segment.ProjL()));
    }

    @Contract(" -> new") @Override
    public @NotNull Neu projR() {
      return new Neu(var, spine.appended(new Segment.ProjR()));
    }
  }

  record Flex(Var var, ImmutableSeq<Segment> spine, LazyValue<Value> result) implements RefValue {
    public Flex(Var var, ImmutableSeq<Segment> spine, Supplier<Value> result) {
      this(var, spine, LazyValue.of(result));
    }

    @Contract("_ -> new") @Override
    public @NotNull Flex apply(Arg arg) {
      return new Flex(var, spine.appended(new Segment.Apply(arg)), result.map(res -> res.apply(arg)));
    }

    @Contract(" -> new") @Override
    public @NotNull Flex projL() {
      return new Flex(var, spine.appended(new Segment.ProjL()), result.map(Value::projL));
    }

    @Contract(" -> new") @Override
    public @NotNull Flex projR() {
      return new Flex(var, spine.appended(new Segment.ProjR()), result.map(Value::projR));
    }
  }
}

