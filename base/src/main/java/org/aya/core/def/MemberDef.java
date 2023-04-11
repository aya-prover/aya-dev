// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.def;

import kala.collection.immutable.ImmutableSeq;
import org.aya.concrete.stmt.decl.ClassDecl;
import org.aya.concrete.stmt.decl.TeleDecl;
import org.aya.core.term.Term;
import org.aya.ref.DefVar;
import org.jetbrains.annotations.NotNull;

public final class MemberDef extends UserDef<Term> {
  public final @NotNull DefVar<MemberDef, TeleDecl.ClassMember> ref;
  public final @NotNull DefVar<ClassDef, ClassDecl> classRef;
  public final boolean coerce;

  public MemberDef(
    @NotNull DefVar<MemberDef, TeleDecl.ClassMember> ref, @NotNull DefVar<ClassDef, ClassDecl> classRef,
    @NotNull ImmutableSeq<Term.Param> telescope, @NotNull Term result, boolean coerce
  ) {
    super(telescope, result);
    ref.core = this;
    this.classRef = classRef;
    this.coerce = coerce;
    this.ref = ref;
  }

  public @NotNull DefVar<MemberDef, TeleDecl.ClassMember> ref() {
    return ref;
  }
}
