// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.generic;

import org.aya.core.def.CtorDef;
import org.aya.core.repr.AyaShape;
import org.aya.core.term.CallTerm;
import org.aya.core.term.Term;
import org.aya.generic.util.InternalException;
import org.jetbrains.annotations.NotNull;

import java.util.function.BiFunction;

public interface Shaped<T> {
  @NotNull AyaShape shape();
  @NotNull Term type();
  @NotNull T constructorForm();
  <O> boolean sameEncoding(@NotNull Shaped<O> other);

  interface Inductively<T> extends Shaped<T> {
    @Override @NotNull CallTerm.Data type();
    @NotNull T makeZero(@NotNull CtorDef zero);
    @NotNull T makeSuc(@NotNull CtorDef suc, @NotNull T t);
    @NotNull T destruct(int repr);
    int repr();

    default @Override <O> boolean sameEncoding(@NotNull Shaped<O> other) {
      if (shape() != other.shape()) return false;
      if (!(other instanceof Inductively otherData)) return false;
      return type().ref().core == otherData.type().ref().core;
    }

    default <O> boolean sameValue(@NotNull Shaped<O> other) {
      if (!sameEncoding(other)) return false;
      var otherData = ((Inductively<O>) other);
      return repr() == otherData.repr();
    }

    default @Override @NotNull T constructorForm() {
      int repr = repr();
      return with((zero, suc) -> {
        if (repr == 0) return makeZero(zero);
        return makeSuc(suc, destruct(repr - 1));
      });
    }

    default <R> R with(@NotNull BiFunction<CtorDef, CtorDef, R> block) {
      var type = type();
      var dataDef = type.ref().core;
      var zeroOpt = dataDef.body.find(it -> it.selfTele.sizeEquals(0));
      var sucOpt = dataDef.body.find(it -> it.selfTele.sizeEquals(1));
      if (zeroOpt.isEmpty() || sucOpt.isEmpty()) throw new InternalException("shape recognition bug");
      var zero = zeroOpt.get();
      var suc = sucOpt.get();
      return block.apply(zero, suc);
    }
  }
}
