// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.def;

import kala.collection.Seq;
import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.value.LazyValue;
import org.aya.syntax.concrete.stmt.decl.ClassDecl;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.ref.DefVar;
import org.aya.syntax.telescope.AbstractTele;
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
  @NotNull ImmutableSeq<MemberDef> members
) implements TopLevelDef {
  public ClassDef { ref.initialize(this); }
  public static final class Delegate extends TyckAnyDef<ClassDef> implements ClassDefLike {
    private final @NotNull LazyValue<ImmutableSeq<MemberDefLike>> members = LazyValue.of(() ->
      core().members.map(x -> new MemberDef.Delegate(x.ref())));

    public Delegate(@NotNull DefVar<ClassDef, ?> ref) { super(ref); }
    @Override public @NotNull ImmutableSeq<MemberDefLike> members() { return members.get(); }
    @Override public @NotNull AbstractTele takeMembers(int size) {
      return new TakeMembers(core(), size);
    }
  }

  record TakeMembers(@NotNull ClassDef clazz, @Override int telescopeSize) implements AbstractTele {
    @Override public boolean telescopeLicit(int i) { return true; }
    @Override public @NotNull String telescopeName(int i) {
      assert i < telescopeSize;
      return clazz.members.get(i).ref().name();
    }

    // class Foo
    // | foo : A
    // | + : A -> A -> A
    // | bar : Fn (x : Foo A) -> (x.foo) self.+ (self.foo)
    //                  instantiate these!   ^       ^
    @Override public @NotNull Term telescope(int i, Seq<Term> teleArgs) {
      // teleArgs are former members
      assert i < telescopeSize;
      var member = clazz.members.get(i);
      // TODO: instantiate self projection with teleArgs
      return TyckDef.defSignature(member.ref()).makePi();
    }
    @Override public @NotNull Term result(Seq<Term> teleArgs) {
      // Use SigmaTerm::lub
      throw new UnsupportedOperationException("TODO");
    }
    @Override public @NotNull SeqView<String> namesView() {
      return clazz.members.sliceView(0, telescopeSize).map(i -> i.ref().name());
    }
  }
}
