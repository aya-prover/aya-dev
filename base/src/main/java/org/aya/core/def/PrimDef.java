// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.def;

import org.aya.api.ref.DefVar;
import org.aya.concrete.Decl;
import org.aya.core.term.Term;
import org.aya.core.term.UnivTerm;
import org.glavo.kala.collection.Map;
import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.glavo.kala.tuple.Tuple;
import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000
 */
public sealed interface PrimDef extends Def {
  @Override default <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
    return visitor.visitPrim(this, p);
  }

  final class Interval implements PrimDef {
    private Interval() {
    }

    public static final @NotNull DefVar<@NotNull Interval, Decl.PrimDecl> ref = DefVar.core(new Interval(), "I");

    @Override public @NotNull ImmutableSeq<Term.Param> contextTele() {
      return ImmutableSeq.empty();
    }

    @Override public @NotNull ImmutableSeq<Term.Param> telescope() {
      return ImmutableSeq.empty();
    }

    @Override public @NotNull DefVar<Interval, Decl.PrimDecl> ref() {
      return ref;
    }

    @Override public @NotNull Term result() {
      return UnivTerm.OMEGA;
    }
  }

  @NotNull Map<String, DefVar<? extends PrimDef, Decl.PrimDecl>> primitives = Map.ofEntries(
    Tuple.of("I", Interval.ref)
  );
}
