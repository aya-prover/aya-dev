// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

import kala.collection.immutable.ImmutableSeq;
import kala.control.Option;
import kala.tuple.Tuple2;
import org.aya.concrete.stmt.Decl;
import org.aya.concrete.stmt.StructDecl;
import org.aya.core.def.FieldDef;
import org.aya.core.def.StructDef;
import org.aya.generic.Arg;
import org.aya.ref.DefVar;
import org.jetbrains.annotations.NotNull;

/**
 * @author zaoqi
 */
public sealed interface StructCall extends Term {
  @NotNull DefVar<StructDef, StructDecl> ref();
  @NotNull Option<DefVar<FieldDef, Decl.StructField>> nextField();
  @NotNull ImmutableSeq<Tuple2<DefVar<FieldDef, Decl.StructField>, Arg<@NotNull Term>>> params();
  int ulift();

  record Ref(
    @NotNull DefVar<StructDef, StructDecl> ref,
    int ulift
  ) implements StructCall {
    @Override
    public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      throw new UnsupportedOperationException("TODO");
    }

    @Override
    public @NotNull Option<DefVar<FieldDef, Decl.StructField>> nextField() {
      return ref.core.allFields().getOption(0).map(FieldDef::ref);
    }

    @Override
    public @NotNull ImmutableSeq<Tuple2<DefVar<FieldDef, Decl.StructField>, Arg<Term>>> params() {
      return ImmutableSeq.empty();
    }
  }

  record Call(
    @NotNull StructCall struct,
    int ulift,
    @NotNull DefVar<FieldDef, Decl.StructField> field,
    @NotNull Arg<@NotNull Term> arg
  ) implements StructCall {
    @Override
    public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return null;
    }

    @Override
    public @NotNull DefVar<StructDef, StructDecl> ref() {
      return struct.ref();
    }

    @Override
    public @NotNull Option<DefVar<FieldDef, Decl.StructField>> nextField() {
      var params = params();
      return ref().core.allFields().findFirst(field -> !params.find(x -> x._1 == field.ref).isDefined()).map(FieldDef::ref);
    }

    @Override
    public @NotNull ImmutableSeq<Tuple2<DefVar<FieldDef, Decl.StructField>, Arg<Term>>> params() {
      return struct.params().appended(Tuple2.of(field, arg));
    }
  }
}
