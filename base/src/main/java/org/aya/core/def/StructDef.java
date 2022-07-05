// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.def;

import kala.collection.Map;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableHashMap;
import kala.collection.mutable.MutableMap;
import org.aya.concrete.stmt.ClassDecl;
import org.aya.core.term.Term;
import org.aya.ref.DefVar;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * core struct definition, corresponding to {@link ClassDecl.StructDecl}
 *
 * @author vont
 */

public final class StructDef extends ClassDef.Type {
  public final @NotNull DefVar<StructDef, ClassDecl.StructDecl> ref;
  public final @NotNull ImmutableSeq<FieldDef> fields;
  public @NotNull Map<DefVar<FieldDef, ClassDecl.StructDecl.StructField>, FieldDef> fieldMap;

  public StructDef(
    @NotNull DefVar<StructDef, ClassDecl.StructDecl> ref,
    int ulift,
    @NotNull ImmutableSeq<FieldDef> fields
  ) {
    super(ulift);
    ref.core = this;
    this.ref = ref;
    this.fields = fields;
    MutableMap<DefVar<FieldDef, ClassDecl.StructDecl.StructField>, FieldDef> fieldMap = MutableHashMap.create();
    this.fieldMap = fieldMap;
    for (var field : fields) {
      var result = fieldMap.put(field.rootRef(), field);
      assert result.isEmpty();
    }
  }

  @Override public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
    return visitor.visitStruct(this, p);
  }

  public @NotNull DefVar<StructDef, ClassDecl.StructDecl> ref() {
    return ref;
  }
}
