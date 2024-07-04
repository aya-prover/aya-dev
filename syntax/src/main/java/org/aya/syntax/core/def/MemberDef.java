// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.def;

import kala.collection.immutable.ImmutableSeq;
import org.aya.syntax.concrete.stmt.decl.ClassDecl;
import org.aya.syntax.concrete.stmt.decl.ClassMember;
import org.aya.syntax.core.term.Param;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.ref.DefVar;
import org.jetbrains.annotations.NotNull;

/**
 * A class field definition.
 *
 * @param telescope it is bound with the `self` pointer, so whenever you need to make sense of this type,
 *                  you need to inst its elements with `self` first.
 */
public record MemberDef(
  @NotNull DefVar<ClassDef, ClassDecl> classRef,
  @Override @NotNull DefVar<MemberDef, ClassMember> ref,
  int index,
  @Override ImmutableSeq<Param> telescope,
  @Override @NotNull Term result
) implements TyckDef {
  public MemberDef {
    assert index >= 0;
    ref.initialize(this);
  }

  public static final class Delegate extends TyckAnyDef<MemberDef> implements MemberDefLike {
    public Delegate(@NotNull DefVar<MemberDef, ?> ref) { super(ref); }

    /**
     * this implementation prevents invocation of {@link ClassDef.Delegate#members()} while tycking {@link ClassDef}
     */
    @Override
    public int index() {
      return ref.core.index;
    }

    @Override public @NotNull ClassDefLike classRef() {
      return new ClassDef.Delegate(core().classRef());
    }
  }
}
