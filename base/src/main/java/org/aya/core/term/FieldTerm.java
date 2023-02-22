// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

import kala.collection.immutable.ImmutableSeq;
import org.aya.concrete.stmt.decl.TeleDecl;
import org.aya.core.def.ClassDef;
import org.aya.core.pat.Pat;
import org.aya.ref.DefVar;
import org.aya.util.Arg;
import org.jetbrains.annotations.NotNull;

import java.util.function.UnaryOperator;

/**
 * @author ice1000
 */
public record FieldTerm(
  @NotNull Term of,
  @NotNull DefVar<ClassDef.Member, TeleDecl.ClassMember> ref,
  @Override @NotNull ImmutableSeq<@NotNull Arg<@NotNull Term>> args
) implements Callable {
  private FieldTerm update(Term struct, ImmutableSeq<Arg<Term>> newArgs) {
    return struct == of && newArgs.sameElements(args, true) ? this
      : new FieldTerm(struct, ref, newArgs);
  }

  @Override public @NotNull FieldTerm descent(@NotNull UnaryOperator<Term> f, @NotNull UnaryOperator<Pat> g) {
    return update(f.apply(of), args.map(arg -> arg.descent(f)));
  }
}
