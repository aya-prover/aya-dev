// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.value;

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

  record Pi(Param param, Function<Value, Value> func) implements FormValue {
    @Override
    public <P, R> R accept(Visitor<P, R> visitor, P p) {
      return visitor.visitPi(this, p);
    }
  }

  record Sigma(Param param, Function<Value, Value> func) implements FormValue {
    @Override
    public <P, R> R accept(Visitor<P, R> visitor, P p) {
      return visitor.visitSigma(this, p);
    }
  }

  record Univ(Sort sort) implements FormValue {
    @Override
    public <P, R> R accept(Visitor<P, R> visitor, P p) {
      return visitor.visitUniv(this, p);
    }
  }
}
