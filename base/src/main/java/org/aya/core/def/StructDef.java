// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.def;

import org.aya.api.ref.DefVar;
import org.aya.concrete.Decl;
import org.aya.core.term.Term;
import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.jetbrains.annotations.NotNull;

/**
 * core struct definition, corresponding to {@link Decl.StructDecl}
 *
 * @author vont
 */

public record StructDef(
  @NotNull DefVar<StructDef, Decl.StructDecl> ref,
  @NotNull ImmutableSeq<Term.Param> telescope,
  @NotNull Term result,
  @NotNull ImmutableSeq<Field> fields
) implements Def {
  public StructDef {
    ref.core = this;
  }

  @Override
  public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
    return visitor.visitStruct(this, p);
  }

  public static record Field(
    @NotNull DefVar<StructDef, Decl.StructDecl> structRef,
    @NotNull DefVar<Field, Decl.StructField> ref,
    @NotNull ImmutableSeq<Term.Param> telescope,
    @NotNull Term result,
    boolean coerce
  ) implements Def {
    public Field {
      ref.core = this;
    }

    @Override public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitField(this, p);
    }
  }
}
