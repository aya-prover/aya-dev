// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.value;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.DynamicSeq;
import org.aya.core.term.FormTerm;
import org.aya.core.term.IntroTerm;
import org.aya.core.term.Term;
import org.aya.ref.LocalVar;

public record Quoter() {
  private Term.Param quote(Value.Param param) {
    return new Term.Param(new LocalVar(param.ref().name()), quote(param.type()), param.explicit());
  }

  public Term quote(Value value) {
    return switch (value) {
      case FormValue.Unit ignore -> new FormTerm.Sigma(ImmutableSeq.empty());
      case FormValue.Pi pi -> {
        var param = quote(pi.param());
        var body = quote(pi.func().apply(new RefValue.Neu(param.ref())));
        yield new FormTerm.Pi(param, body);
      }
      case FormValue.Sigma sigma -> {
        var param = quote(sigma.param());
        var body = quote(sigma.func().apply(new RefValue.Neu(param.ref())));
        var params = DynamicSeq.of(param);
        if (body instanceof FormTerm.Sigma sig) {
          params.appendAll(sig.params());
        } else {
          params.append(new Term.Param(new LocalVar("_"), body, true));
        }
        yield new FormTerm.Sigma(params.toImmutableSeq());
      }
      case FormValue.Univ univ -> new FormTerm.Univ(univ.sort());
      case IntroValue.TT ignore -> new IntroTerm.Tuple(ImmutableSeq.empty());
      case IntroValue.Pair pair -> {
        var left = quote(pair.left());
        var items = DynamicSeq.of(left);
        var right = quote(pair.right());
        if (right instanceof IntroTerm.Tuple tup) {
          items.appendAll(tup.items());
        } else {
          // Shouldn't happen given the way we evaluate tuples, but anyway...
          items.append(right);
        }
        yield new IntroTerm.Tuple(items.toImmutableSeq());
      }
      case IntroValue.Lam lam -> {
        var param = quote(lam.param());
        var body = quote(lam.func().apply(new RefValue.Neu(param.ref())));
        yield new IntroTerm.Lambda(param, body);
      }
      default -> null;
    };
  }
}
