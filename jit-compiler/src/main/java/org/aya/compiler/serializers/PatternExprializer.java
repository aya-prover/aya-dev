// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.serializers;

import kala.collection.immutable.ImmutableSeq;
import org.aya.compiler.morphism.ExprBuilder;
import org.aya.compiler.morphism.JavaExpr;
import org.aya.compiler.serializers.ModuleSerializer.MatchyRecorder;
import org.aya.syntax.core.pat.Pat;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.core.term.call.ConCallLike;
import org.aya.syntax.ref.LocalVar;
import org.aya.util.Panic;
import org.jetbrains.annotations.NotNull;

public final class PatternExprializer extends AbstractExprializer<Pat> {
  private final boolean allowLocalTerm;

  PatternExprializer(
          @NotNull ExprBuilder builder, @NotNull SerializerContext context, boolean allowLocalTerm
  ) {
    super(builder, context);
    this.allowLocalTerm = allowLocalTerm;
  }

  private @NotNull JavaExpr serializeTerm(@NotNull Term term) {
    return new TermExprializer(builder, ImmutableSeq.empty(), allowLocalTerm)
      .serialize(term);
  }

  private @NotNull JavaExpr serializeConHead(@NotNull ConCallLike.Head head) {
    var termSer = new TermExprializer(builder, ImmutableSeq.empty(), allowLocalTerm, recorder);

    return builder.mkNew(ConCallLike.Head.class, ImmutableSeq.of(
      getInstance(head.ref()),
      builder.iconst(head.ulift()),
      makeImmutableSeq(Term.class, head.ownerArgs().map(termSer::serialize))
    ));
  }

  @Override protected @NotNull JavaExpr doSerialize(@NotNull Pat term) {
    return switch (term) {
      case Pat.Misc misc -> builder.refEnum(misc);
      // it is safe to new a LocalVar, this method will be called when meta solving only,
      // but the meta solver will eat all LocalVar so that it will be happy.
      case Pat.Bind bind -> builder.mkNew(Pat.Bind.class, ImmutableSeq.of(
        builder.mkNew(LocalVar.class, ImmutableSeq.of(builder.aconst(bind.bind().name()))),
        serializeTerm(bind.type())
      ));
      case Pat.Con con -> builder.mkNew(Pat.Con.class, ImmutableSeq.of(
        serializeToImmutableSeq(Pat.class, con.args()),
        serializeConHead(con.head())
      ));
      case Pat.ShapedInt shapedInt -> builder.mkNew(Pat.ShapedInt.class, ImmutableSeq.of(
        builder.iconst(shapedInt.repr()),
        getInstance(shapedInt.zero()),
        getInstance(shapedInt.suc()),
        serializeTerm(shapedInt.type())
      ));
      case Pat.Meta _ -> Panic.unreachable();
      case Pat.Tuple(var l, var r) -> builder.mkNew(Pat.Tuple.class, ImmutableSeq.of(
        doSerialize(l), doSerialize(r)
      ));
    };
  }

  @Override public @NotNull JavaExpr serialize(Pat unit) { return doSerialize(unit); }
}
