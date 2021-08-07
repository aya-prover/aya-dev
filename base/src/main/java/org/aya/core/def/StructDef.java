// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.def;

import kala.collection.immutable.ImmutableSeq;
import org.aya.api.ref.DefVar;
import org.aya.concrete.stmt.Decl;
import org.aya.core.sort.Sort;
import org.aya.core.term.Term;
import org.jetbrains.annotations.NotNull;

/**
 * core struct definition, corresponding to {@link Decl.StructDecl}
 *
 * @author vont
 */

public final class StructDef implements Def {
  public final @NotNull DefVar<StructDef, Decl.StructDecl> ref;
  public final @NotNull ImmutableSeq<Term.Param> telescope;
  public final @NotNull ImmutableSeq<Sort.LvlVar> levels;
  public final @NotNull Term result;
  public final @NotNull ImmutableSeq<FieldDef> fields;

  public StructDef(@NotNull DefVar<StructDef, Decl.StructDecl> ref, @NotNull ImmutableSeq<Term.Param> telescope, @NotNull ImmutableSeq<Sort.LvlVar> levels, @NotNull Term result, @NotNull ImmutableSeq<FieldDef> fields) {
    ref.core = this;
    this.ref = ref;
    this.telescope = telescope;
    this.levels = levels;
    this.result = result;
    this.fields = fields;
  }

  @Override public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
    return visitor.visitStruct(this, p);
  }

  public @NotNull DefVar<StructDef, Decl.StructDecl> ref() {
    return ref;
  }

  public @NotNull ImmutableSeq<Term.Param> telescope() {
    return telescope;
  }

  public @NotNull ImmutableSeq<Sort.LvlVar> levels() {
    return levels;
  }

  public @NotNull Term result() {
    return result;
  }
}
