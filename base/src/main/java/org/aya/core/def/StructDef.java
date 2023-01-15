// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.def;

import kala.collection.immutable.ImmutableSeq;
import org.aya.concrete.stmt.decl.TeleDecl;
import org.aya.core.term.SortTerm;
import org.aya.core.term.Term;
import org.aya.ref.DefVar;
import org.jetbrains.annotations.NotNull;

/**
 * core struct definition, corresponding to {@link TeleDecl.StructDecl}
 *
 * @author vont
 */

public final class StructDef extends UserDef.Type {
  public final @NotNull DefVar<StructDef, TeleDecl.StructDecl> ref;
  public final @NotNull ImmutableSeq<FieldDef> fields;

  public StructDef(
    @NotNull DefVar<StructDef, TeleDecl.StructDecl> ref,
    @NotNull ImmutableSeq<Term.Param> telescope,
    SortTerm ulift,
    @NotNull ImmutableSeq<FieldDef> fields
  ) {
    super(telescope, ulift);
    ref.core = this;
    this.ref = ref;
    this.fields = fields;
  }

  public @NotNull DefVar<StructDef, TeleDecl.StructDecl> ref() {
    return ref;
  }
}
