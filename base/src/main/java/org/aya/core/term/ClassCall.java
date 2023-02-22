// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

import kala.collection.immutable.ImmutableSeq;
import org.aya.concrete.stmt.decl.ClassDecl;
import org.aya.core.def.ClassDef;
import org.aya.core.pat.Pat;
import org.aya.ref.DefVar;
import org.aya.util.Arg;
import org.jetbrains.annotations.NotNull;

import java.util.function.UnaryOperator;

/**
 * ClassCall is a very special construction in Aya.
 * <ul>
 *   <li>It is like a type when partially instantiated -- the type of "specifications" of the rest of the fields.</li>
 *   <li>It is like a term when fully instantiated, whose type can be anything.</li>
 *   <li>It can be applied like a function, which essentially inserts the nearest missing field.</li>
 * </ul>
 *
 * @author kiva
 */
public record ClassCall(
  @Override @NotNull DefVar<ClassDef, ClassDecl> ref,
  @Override int ulift,
  @Override @NotNull ImmutableSeq<Arg<@NotNull Term>> args
) implements StableWHNF, Formation, Callable.Common {
  public @NotNull ClassCall update(@NotNull ImmutableSeq<Arg<Term>> args) {
    return args.sameElements(args(), true) ? this : new ClassCall(ref(), ulift(), args);
  }

  @Override public @NotNull ClassCall descent(@NotNull UnaryOperator<@NotNull Term> f, @NotNull UnaryOperator<Pat> g) {
    return update(args.map(arg -> arg.descent(f)));
  }
}
