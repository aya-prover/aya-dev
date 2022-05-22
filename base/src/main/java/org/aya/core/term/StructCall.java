// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

import kala.collection.immutable.ImmutableMap;
import kala.control.Option;
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
  @NotNull ImmutableMap<DefVar<FieldDef, Decl.StructField>, Arg<@NotNull Term>> params();
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
    public @NotNull ImmutableMap<DefVar<FieldDef, Decl.StructField>, Arg<@NotNull Term>> params() {
      return ImmutableMap.empty();
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
      return ref().core.allFields().find(field -> !params.containsKey(field.ref)).map(FieldDef::ref);
    }

    @Override
    public @NotNull ImmutableMap<DefVar<FieldDef, Decl.StructField>, Arg<@NotNull Term>> params() {
      return struct.params().putted(field, arg);
    }
  }
}
