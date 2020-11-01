// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.tyck.unify;

import asia.kala.collection.mutable.ArrayBuffer;
import asia.kala.collection.mutable.Buffer;
import org.jetbrains.annotations.Nullable;
import org.mzi.api.ref.Var;
import org.mzi.core.term.RefTerm;
import org.mzi.core.term.Term;
import org.mzi.generic.Arg;
import org.mzi.ref.LocalVar;

/**
 * The implementation of untyped pattern unification for holes.
 * András Kovács' elaboration-zoo is taken as reference.
 *
 * @author ice1000
 */
public class PatDefEq {
  private @Nullable Buffer<Var> extract(Buffer<Arg<Term>> spine) {
    var buf = new ArrayBuffer<Var>(spine.size());
    for (var arg : spine) {
      if (arg.term() instanceof RefTerm ref && ref.var() instanceof LocalVar var) buf.append(var);
      else return null;
    }
    return buf;
  }
}
