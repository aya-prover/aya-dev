// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler;

import kala.collection.immutable.ImmutableSeq;
import org.aya.generic.NameGenerator;
import org.aya.syntax.core.pat.Pat;
import org.aya.syntax.core.term.ErrorTerm;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.core.term.call.ConCallLike;
import org.aya.syntax.ref.LocalVar;
import org.aya.util.error.Panic;
import org.jetbrains.annotations.NotNull;

import static org.aya.compiler.AyaSerializer.CLASS_PAT;
import static org.aya.compiler.AyaSerializer.CLASS_TERM;

public class PatternExprializer extends AbstractExprializer<Pat> {
  public static final @NotNull String CLASS_PAT_ABSURD = ExprializeUtils.makeSub(CLASS_PAT, ExprializeUtils.getJavaReference(Pat.Absurd.class));
  public static final @NotNull String CLASS_PAT_BIND = ExprializeUtils.makeSub(CLASS_PAT, ExprializeUtils.getJavaReference(Pat.Bind.class));
  public static final @NotNull String CLASS_PAT_CON = ExprializeUtils.makeSub(CLASS_PAT, ExprializeUtils.getJavaReference(Pat.Con.class));
  public static final @NotNull String CLASS_PAT_INT = ExprializeUtils.makeSub(CLASS_PAT, ExprializeUtils.getJavaReference(Pat.ShapedInt.class));
  public static final @NotNull String CLASS_LOCALVAR = ExprializeUtils.getJavaReference(LocalVar.class);
  public static final @NotNull String CLASS_CONHEAD = ExprializeUtils.makeSub(ExprializeUtils.getJavaReference(ConCallLike.class), ExprializeUtils.getJavaReference(ConCallLike.Head.class));
  public static final @NotNull String CLASS_ERROR = ExprializeUtils.getJavaReference(ErrorTerm.class);
  public static final @NotNull String CLASS_PAT_TUPLE = ExprializeUtils.makeSub(CLASS_PAT, ExprializeUtils.getJavaReference(Pat.Tuple.class));

  private final boolean allowLocalTerm;

  protected PatternExprializer(@NotNull NameGenerator nameGen, boolean allowLocalTerm) {
    super(nameGen);
    this.allowLocalTerm = allowLocalTerm;
  }

  private @NotNull String serializeTerm(@NotNull Term term) {
    return new TermExprializer(this.nameGen, ImmutableSeq.empty(), allowLocalTerm)
      .serialize(term);
  }

  private @NotNull String serializeConHead(@NotNull ConCallLike.Head head) {
    return ExprializeUtils.makeNew(CLASS_CONHEAD,
      ExprializeUtils.getInstance(NameSerializer.getClassReference(head.ref())),
      Integer.toString(head.ulift()),
      ExprializeUtils.makeImmutableSeq(CLASS_TERM, head.ownerArgs().map(this::serializeTerm)));
  }

  @Override
  protected @NotNull String doSerialize(@NotNull Pat term) {
    return switch (term) {
      case Pat.Absurd _ -> ExprializeUtils.getInstance(CLASS_PAT_ABSURD);
      // it is safe to new a LocalVar, this method will be called when meta solving only,
      // but the meta solver will eat all LocalVar so that it will be happy.
      case Pat.Bind bind -> ExprializeUtils.makeNew(CLASS_PAT_BIND,
        ExprializeUtils.makeNew(CLASS_LOCALVAR, ExprializeUtils.makeString(bind.bind().name())),
        serializeTerm(bind.type())
      );
      case Pat.Con con -> ExprializeUtils.makeNew(CLASS_PAT_CON,
        ExprializeUtils.getInstance(NameSerializer.getClassReference(con.ref())),
        serializeToImmutableSeq(CLASS_PAT, con.args()),
        serializeConHead(con.head()));
      case Pat.ShapedInt shapedInt -> ExprializeUtils.makeNew(CLASS_PAT_INT,
        Integer.toString(shapedInt.repr()),
        ExprializeUtils.getInstance(NameSerializer.getClassReference(shapedInt.zero())),
        ExprializeUtils.getInstance(NameSerializer.getClassReference(shapedInt.suc())),
        serializeTerm(shapedInt.type()));
      case Pat.Meta _ -> Panic.unreachable();
      case Pat.Tuple tuple -> ExprializeUtils.makeNew(CLASS_PAT_TUPLE,
        serializeToImmutableSeq(CLASS_PAT, tuple.elements()));
    };
  }

  @Override
  public @NotNull String serialize(Pat unit) {
    return doSerialize(unit);
  }
}
