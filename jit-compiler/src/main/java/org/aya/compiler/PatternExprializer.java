// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler;

import kala.collection.immutable.ImmutableSeq;
import org.aya.compiler.free.FreeExprBuilder;
import org.aya.compiler.free.FreeJavaExpr;
import org.aya.syntax.core.pat.Pat;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.core.term.call.ConCallLike;
import org.aya.syntax.ref.LocalVar;
import org.aya.util.error.Panic;
import org.jetbrains.annotations.NotNull;

public final class PatternExprializer extends AbstractExprializer<Pat> {
  private final boolean allowLocalTerm;

  PatternExprializer(@NotNull FreeExprBuilder builder, boolean allowLocalTerm) {
    super(builder);
    this.allowLocalTerm = allowLocalTerm;
  }

  private @NotNull FreeJavaExpr serializeTerm(@NotNull Term term) {
    return new TermExprializer(builder, ImmutableSeq.empty(), allowLocalTerm)
      .serialize(term);
  }

  private @NotNull FreeJavaExpr serializeConHead(@NotNull ConCallLike.Head head) {
    var termSer = new TermExprializer(builder, ImmutableSeq.empty(), allowLocalTerm);

    return builder.mkNew(ConCallLike.Head.class, ImmutableSeq.of(
      getInstance(head.ref()),
      builder.iconst(head.ulift()),
      makeImmutableSeq(Term.class, head.ownerArgs().map(termSer::serialize))
    ));
  }

  @Override protected @NotNull FreeJavaExpr doSerialize(@NotNull Pat term) {
    return switch (term) {
      case Pat.Misc misc -> builder.refEnum(misc);
      // it is safe to new a LocalVar, this method will be called when meta solving only,
      // but the meta solver will eat all LocalVar so that it will be happy.
      case Pat.Bind bind -> builder.mkNew(Pat.Bind.class, ImmutableSeq.of(
        builder.mkNew(LocalVar.class, ImmutableSeq.of(builder.aconst(bind.bind().name()))),
        serializeTerm(bind.type())
      ));
      case Pat.Con con -> builder.mkNew(Pat.Con.class, ImmutableSeq.of(
        getInstance(con.ref()),
        serializeToImmutableSeq(Pat.class, con.args()),
        serializeConHead(con.head())
      ));
      case Pat.ShapedInt shapedInt -> builder.mkNew(Pat.ShapedInt.class, ImmutableSeq.of(
        builder.iconst(shapedInt.repr()),
        getInstance(shapedInt.zero()),
        getInstance(shapedInt.suc()),
        serializeTerm(shapedInt.toTerm())
      ));
      case Pat.Meta _ -> Panic.unreachable();
      case Pat.Tuple(var l, var r) -> builder.mkNew(Pat.Tuple.class, ImmutableSeq.of(
        doSerialize(l), doSerialize(r)
      ));
    };
  }

  @Override public @NotNull FreeJavaExpr serialize(Pat unit) { return doSerialize(unit); }
}
