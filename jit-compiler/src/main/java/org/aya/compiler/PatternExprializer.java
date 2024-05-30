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

import static org.aya.compiler.AbstractSerializer.*;

public class PatternExprializer extends AbstractExprializer<Pat> {
  public static final String CLASS_PAT = getJavaReference(Pat.class);
  public static final @NotNull String CLASS_PAT_ABSURD = makeSub(CLASS_PAT, getJavaReference(Pat.Absurd.class));
  public static final @NotNull String CLASS_PAT_BIND = makeSub(CLASS_PAT, getJavaReference(Pat.Bind.class));
  public static final @NotNull String CLASS_PAT_CON = makeSub(CLASS_PAT, getJavaReference(Pat.Con.class));
  public static final @NotNull String CLASS_PAT_INT = makeSub(CLASS_PAT, getJavaReference(Pat.ShapedInt.class));
  public static final @NotNull String CLASS_LOCALVAR = getJavaReference(LocalVar.class);
  public static final @NotNull String CLASS_CONHEAD = makeSub(getJavaReference(ConCallLike.class), getJavaReference(ConCallLike.Head.class));
  public static final @NotNull String CLASS_ERROR = getJavaReference(ErrorTerm.class);
  public static final @NotNull String CLASS_PAT_TUPLE = makeSub(CLASS_PAT, getJavaReference(Pat.Tuple.class));

  private final boolean allowLocalTerm;

  protected PatternExprializer(@NotNull NameGenerator nameGen, boolean allowLocalTerm) {
    super(nameGen);
    this.allowLocalTerm = allowLocalTerm;
  }

  private @NotNull String serializeTerm(@NotNull Term term) {
    return new TermExprializer(this.nameGen, ImmutableSeq.empty(), allowLocalTerm)
      .serialize(term).result();
  }

  private @NotNull String serializeConHead(@NotNull ConCallLike.Head head) {
    return makeNew(CLASS_CONHEAD,
      getInstance(NameSerializer.getClassReference(head.ref())),
      Integer.toString(head.ulift()),
      makeImmutableSeq(CLASS_TERM, head.ownerArgs().map(this::serializeTerm)));
  }

  @Override
  protected @NotNull String doSerialize(@NotNull Pat term) {
    return switch (term) {
      case Pat.Absurd _ -> getInstance(CLASS_PAT_ABSURD);
      // it is safe to new a LocalVar, this method will be called when meta solving only,
      // but the meta solver will eat all LocalVar so that it will be happy.
      case Pat.Bind bind -> makeNew(CLASS_PAT_BIND,
        makeNew(CLASS_LOCALVAR, makeString(bind.bind().name())),
        serializeTerm(bind.type())
      );
      case Pat.Con con -> makeNew(CLASS_PAT_CON,
        getInstance(NameSerializer.getClassReference(con.ref())),
        serializeToImmutableSeq(CLASS_PAT, con.args()),
        serializeConHead(con.head()));
      case Pat.ShapedInt shapedInt -> makeNew(CLASS_PAT_INT,
        Integer.toString(shapedInt.repr()),
        getInstance(NameSerializer.getClassReference(shapedInt.zero())),
        getInstance(NameSerializer.getClassReference(shapedInt.suc())),
        serializeTerm(shapedInt.type()));
      case Pat.Meta _ -> Panic.unreachable();
      case Pat.Tuple tuple -> makeNew(CLASS_PAT_TUPLE,
        serializeToImmutableSeq(CLASS_PAT, tuple.elements()));
    };
  }
}
