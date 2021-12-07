// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.value;

import kala.collection.immutable.ImmutableSeq;
import org.aya.core.def.DataDef;
import org.aya.core.def.StructDef;
import org.aya.core.sort.Sort;
import org.aya.core.term.FormTerm;
import org.aya.util.error.SourcePos;

import java.util.function.Function;

public sealed interface FormValue extends Value {
  record Unit() implements FormValue {}

  record Sigma(Param param, Function<Value, Value> func) implements FormValue {}

  record Pi(Param param, Function<Value, Value> func) implements FormValue {}

  record Data(DataDef def, ImmutableSeq<Arg> args) implements FormValue {}

  record Struct(StructDef def, ImmutableSeq<Arg> args) implements FormValue {}

  record Univ(Sort sort) implements FormValue {
    public static Univ fresh(SourcePos pos) {
      return new Univ(FormTerm.freshSort(pos));
    }
  }
}
