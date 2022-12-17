// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.value;

import kala.collection.immutable.ImmutableMap;
import kala.collection.immutable.ImmutableSeq;
import org.aya.core.def.CtorDef;
import org.aya.core.def.FieldDef;

import java.util.function.Function;

public sealed interface IntroValue extends Value {
  record TT() implements IntroValue {}

  record Pair(Value left, Value right) implements IntroValue {
    @Override
    public Value projL() {
      return left;
    }

    @Override
    public Value projR() {
      return right;
    }
  }

  record Lam(Param param, Function<Value, Value> func) implements IntroValue {
    @Override
    public Value apply(Arg arg) {
      assert arg.explicit() == param.explicit();
      return func.apply(arg.value());
    }
  }

  record Ctor(CtorDef def, ImmutableSeq<Arg> args, FormValue.Data data) implements IntroValue {}

  record New(ImmutableMap<FieldDef, Value> fields, FormValue.Struct struct) implements IntroValue {
    public Value access(FieldDef field) {
      return fields.get(field);
    }
  }
}
