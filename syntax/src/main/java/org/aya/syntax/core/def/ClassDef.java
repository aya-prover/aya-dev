// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.def;

import kala.collection.immutable.ImmutableSeq;
import kala.value.LazyValue;
import org.aya.syntax.concrete.stmt.decl.ClassDecl;
import org.aya.syntax.ref.DefVar;
import org.jetbrains.annotations.NotNull;

/**
 * <pre>
 * class Cat
 * | A : Type
 * | Hom (a b : A) : Type
 * | id (a : A) : Hom a a
 * </pre>
 *
 * <ul>
 *   <li>{@code Cat : Type1}, the type of all categories</li>
 *   <li>{@code Cat A : Type1}, the type of all categories where the object type is A</li>
 *   <li>{@code Cat A (->) : Type0}, essentially type of {@code A -> A}</li>
 * </ul>
 */
public record ClassDef(
  @Override @NotNull DefVar<ClassDef, ClassDecl> ref,
  @NotNull ImmutableSeq<MemberDef> members,
  int classifyingIndex
) implements TopLevelDef {
  public ClassDef { ref.initialize(this); }
  public @NotNull MemberDef classifyingField() {
    assert classifyingIndex != -1;
    return members.get(classifyingIndex);
  }
  public static final class Delegate extends TyckAnyDef<ClassDef> implements ClassDefLike {
    private final @NotNull LazyValue<ImmutableSeq<MemberDef.Delegate>> members = LazyValue.of(() ->
      core().members.map(x -> new MemberDef.Delegate(x.ref())));

    public Delegate(@NotNull DefVar<ClassDef, ?> ref) { super(ref); }
    @Override public @NotNull ImmutableSeq<MemberDef.Delegate> members() { return members.get(); }
    @Override public int classifyingIndex() { return ref.core.classifyingIndex; }
  }
}
