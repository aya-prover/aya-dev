// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.value;

import java.util.function.Function;

public sealed interface IntroValue extends Value {
  record Lambda(Param param, Function<Value, Value> func) implements IntroValue {

  }

  record Pair(Value left, Value right) implements IntroValue {

  }
}
