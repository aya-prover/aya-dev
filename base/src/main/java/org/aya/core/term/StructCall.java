// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

import kala.collection.immutable.ImmutableSeq;
import kala.control.Option;
import kala.tuple.Tuple2;
import org.aya.concrete.stmt.Decl;
import org.aya.concrete.stmt.StructDecl;
import org.aya.concrete.stmt.TopTeleDecl;
import org.aya.core.def.FieldDef;
import org.aya.core.def.StructDef;
import org.aya.generic.Arg;
import org.aya.ref.DefVar;
import org.jetbrains.annotations.NotNull;

/**
 * @author zaoqi
 */
public record StructCall(
  @NotNull DefVar<StructDef, StructDecl> ref,
  int ulift,
  @NotNull ImmutableSeq<Tuple2<DefVar<FieldDef, TopTeleDecl.StructField>, Arg<@NotNull Term>>> params
) implements Term {
  @Override
  public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
    return visitor.visitStructCall(this, p);
  }

  public @NotNull Option<DefVar<FieldDef, TopTeleDecl.StructField>> nextField() {
    var params = params();
    return ref().core.allFields().findFirst(field -> !params.find(x -> x._1 == field.ref).isDefined()).map(FieldDef::ref);
  }

  public @NotNull StructCall apply(@NotNull FieldDef field, @NotNull Arg<Term> arg) {
    return new StructCall(ref, ulift, params.appended(Tuple2.of(field.ref(), arg)));
  }
}
