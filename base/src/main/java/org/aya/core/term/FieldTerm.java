// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

import kala.collection.immutable.ImmutableSeq;
import org.aya.concrete.stmt.TeleDecl;
import org.aya.core.def.FieldDef;
import org.aya.util.Arg;
import org.aya.ref.DefVar;
import org.jetbrains.annotations.NotNull;

import java.util.function.UnaryOperator;

/**
 * @author ice1000
 */
public record FieldTerm(
  @NotNull Term of,
  @NotNull DefVar<FieldDef, TeleDecl.StructField> ref,
  @NotNull ImmutableSeq<@NotNull Arg<@NotNull Term>> structArgs,
  @NotNull ImmutableSeq<@NotNull Arg<@NotNull Term>> fieldArgs
) implements Callable {
  private FieldTerm update(Term of, ImmutableSeq<Arg<Term>> structArgs, ImmutableSeq<Arg<Term>> fieldArgs) {
    return of == of() && structArgs.sameElements(structArgs(), true) && fieldArgs.sameElements(fieldArgs(), true) ? this
      : new FieldTerm(of, ref, structArgs, fieldArgs);
  }

  @Override public @NotNull FieldTerm descent(@NotNull UnaryOperator<@NotNull Term> f) {
    return update(f.apply(of), structArgs.map(arg -> arg.descent(f)), fieldArgs.map(arg -> arg.descent(f)));
  }

  @Override public @NotNull ImmutableSeq<@NotNull Arg<Term>> args() {
    return structArgs.concat(fieldArgs);
  }
}
