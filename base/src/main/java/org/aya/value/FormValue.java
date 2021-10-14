// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.value;

import org.aya.core.sort.Sort;

import java.util.function.Function;

public sealed interface FormValue extends Value {
  record Unit() implements FormValue {}

  record Pi(Param param, Function<Value, Value> func) implements FormValue {

  }

  record Sig(Param param, Function<Value, Value> func) implements FormValue {

  }

  record Univ(Sort sort) implements FormValue {

  }
}
