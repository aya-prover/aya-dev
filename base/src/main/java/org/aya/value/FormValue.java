// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.value;

import kala.collection.immutable.ImmutableSeq;
import org.aya.core.def.DataDef;
import org.aya.core.def.StructDef;
import org.aya.core.sort.Sort;
import org.aya.value.visitor.Visitor;

import java.util.function.Function;

public sealed interface FormValue extends Value {
  record Unit() implements FormValue {
    @Override
    public <P, R> R accept(Visitor<P, R> visitor, P p) {
      return visitor.visitUnit(this, p);
    }
  }

  record Sigma(Param param, Function<Value, Value> func) implements FormValue {
    @Override
    public <P, R> R accept(Visitor<P, R> visitor, P p) {
      return visitor.visitSigma(this, p);
    }
  }

  record Pi(Param param, Function<Value, Value> func) implements FormValue {
    @Override
    public <P, R> R accept(Visitor<P, R> visitor, P p) {
      return visitor.visitPi(this, p);
    }
  }

  record Data(DataDef def, ImmutableSeq<Arg> args) implements FormValue {
    @Override
    public <P, R> R accept(Visitor<P, R> visitor, P p) {
      return visitor.visitData(this, p);
    }
  }

  record Struct(StructDef def, ImmutableSeq<Arg> args) implements FormValue {
    @Override
    public <P, R> R accept(Visitor<P, R> visitor, P p) {
      return visitor.visitStruct(this, p);
    }
  }

  record Univ(Sort sort) implements FormValue {
    @Override
    public <P, R> R accept(Visitor<P, R> visitor, P p) {
      return visitor.visitUniv(this, p);
    }
  }
}
