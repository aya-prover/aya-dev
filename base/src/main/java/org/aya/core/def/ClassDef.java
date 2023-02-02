// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.def;

import kala.collection.immutable.ImmutableSeq;
import org.aya.concrete.stmt.decl.ClassDecl;
import org.aya.concrete.stmt.decl.TeleDecl;
import org.aya.core.term.SortTerm;
import org.aya.core.term.Term;
import org.aya.generic.AyaDocile;
import org.aya.ref.DefVar;
import org.jetbrains.annotations.NotNull;

public final class ClassDef implements AyaDocile, GenericDef {
  public final DefVar<ClassDef, ClassDecl> ref;
  public final @NotNull SortTerm result;

  public ClassDef(@NotNull DefVar<ClassDef, ClassDecl> ref, @NotNull SortTerm result) {
    ref.core = this;
    this.ref = ref;
    this.result = result;
  }

  @Override public @NotNull DefVar<? extends ClassDef, ? extends ClassDecl> ref() {
    return ref;
  }

  @Override public @NotNull SortTerm result() {
    return result;
  }

  public static final class Member extends UserDef<Term> {
    public final @NotNull DefVar<Member, TeleDecl.ClassMember> ref;

    public Member(
      @NotNull DefVar<Member, TeleDecl.ClassMember> ref,
      @NotNull ImmutableSeq<Term.Param> telescope, @NotNull Term result
    ) {
      super(telescope, result);
      ref.core = this;
      this.ref = ref;
    }

    public @NotNull DefVar<Member, TeleDecl.ClassMember> ref() {
      return ref;
    }
  }
}
