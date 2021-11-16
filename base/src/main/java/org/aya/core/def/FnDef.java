// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.def;

import kala.collection.immutable.ImmutableArray;
import kala.collection.immutable.ImmutableSeq;
import kala.control.Either;
import org.aya.api.ref.DefVar;
import org.aya.concrete.stmt.Decl;
import org.aya.core.Matching;
import org.aya.core.sort.Sort;
import org.aya.core.term.Term;
import org.aya.generic.Modifier;
import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * @author ice1000
 */
public final class FnDef extends UserDef {
  public final @NotNull EnumSet<Modifier> modifiers;
  public final @NotNull DefVar<FnDef, Decl.FnDecl> ref;
  public final @NotNull Either<Term, ImmutableArray<Matching>> body;

  public FnDef(
    @NotNull DefVar<FnDef, Decl.FnDecl> ref, @NotNull ImmutableSeq<Term.Param> telescope,
    @NotNull ImmutableSeq<Sort.LvlVar> levels, @NotNull Term result,
    @NotNull EnumSet<Modifier> modifiers,
    @NotNull Either<Term, ImmutableSeq<Matching>> body
  ) {
    super(telescope, result, levels);
    this.modifiers = modifiers;
    ref.core = this;
    this.ref = ref;
    this.body = body.map(Function.identity(), ImmutableArray::from);
  }

  public static <T> BiFunction<Term, Either<Term, ImmutableSeq<Matching>>, T>
  factory(BiFunction<Term, Either<Term, ImmutableSeq<Matching>>, T> function) {
    return function;
  }

  @Override public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
    return visitor.visitFn(this, p);
  }

  public @NotNull DefVar<FnDef, Decl.FnDecl> ref() {
    return ref;
  }
}
