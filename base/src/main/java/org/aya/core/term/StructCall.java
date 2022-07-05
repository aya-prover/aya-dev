// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

import kala.collection.Map;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableHashMap;
import kala.collection.mutable.MutableMap;
import kala.control.Option;
import kala.tuple.Tuple2;
import org.aya.concrete.stmt.ClassDecl;
import org.aya.core.def.FieldDef;
import org.aya.core.def.StructDef;
import org.aya.generic.Arg;
import org.aya.ref.DefVar;
import org.jetbrains.annotations.NotNull;

/**
 * @author zaoqi
 */
public record StructCall(
  @NotNull DefVar<StructDef, ClassDecl.StructDecl> ref,
  int ulift,
  // use rootRef
  @NotNull ImmutableSeq<Tuple2<DefVar<FieldDef, ClassDecl.StructDecl.StructField>, Arg<@NotNull Term>>> params
) implements Term {
  @Override
  public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
    return visitor.visitStructCall(this, p);
  }

  public @NotNull Option<DefVar<FieldDef, ClassDecl.StructDecl.StructField>> nextField() {
    var params = params();
    return ref().core.fields.findFirst(field -> !params.find(x -> x._1 == field.rootRef()).isDefined()).map(FieldDef::rootRef);
  }

  public boolean finished() {
    return nextField().isEmpty();
  }

  public @NotNull Map<DefVar<FieldDef, ClassDecl.StructDecl.StructField>, Arg<@NotNull Term>> paramsMap() {
    return MutableMap.from(params);
  }

  public @NotNull StructCall apply(@NotNull FieldDef field, @NotNull Arg<Term> arg) {
    return new StructCall(ref, ulift, params.appended(Tuple2.of(field.rootRef(), arg)));
  }
}
