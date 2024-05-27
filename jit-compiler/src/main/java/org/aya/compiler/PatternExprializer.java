// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler;

import kala.collection.immutable.ImmutableSeq;
import org.aya.generic.NameGenerator;
import org.aya.syntax.core.pat.Pat;
import org.aya.syntax.core.term.ErrorTerm;
import org.aya.syntax.core.term.Term;
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
  public static final @NotNull String CLASS_ERROR = getJavaReference(ErrorTerm.class);
  public static final @NotNull String CLASS_PAT_TUPLE = makeSub(CLASS_PAT, getJavaReference(Pat.Tuple.class));


  protected PatternExprializer(@NotNull NameGenerator nameGen) {
    super(nameGen);
  }

  private @NotNull String serializeTerm(@NotNull Term term) {
    return new TermExprializer(this.nameGen, ImmutableSeq.empty())
      .serialize(term).result();
  }

  @Override
  protected @NotNull String doSerialize(@NotNull Pat term) {
    return switch (term) {
      case Pat.Absurd _ -> getInstance(CLASS_PAT_ABSURD);
      // it is safe to new a LocalVar, this method will be called when meta solving only,
      // but the meta solver will eat all LocalVar so that it will be happy.
      case Pat.Bind bind -> makeNew(CLASS_PAT_BIND,
        makeNew(CLASS_LOCALVAR, makeString(bind.bind().name())),
        STR."\{CLASS_ERROR}.DUMMY"
      );
      case Pat.Con con -> makeNew(CLASS_PAT_CON,
        getInstance(getReference(con.ref())),
        serializeToImmutableSeq(CLASS_PAT, con.args()),
        new TermExprializer(this.nameGen, ImmutableSeq.empty())
          .serialize(con.data()).result());
      case Pat.ShapedInt shapedInt -> makeNew(CLASS_PAT_INT,
        Integer.toString(shapedInt.repr()),
        getInstance(getReference(shapedInt.zero())),
        getInstance(getReference(shapedInt.suc())),
        serializeTerm(shapedInt.type()));
      case Pat.Meta _ -> Panic.unreachable();
      case Pat.Tuple tuple -> makeNew(CLASS_PAT_TUPLE,
        serializeToImmutableSeq(CLASS_PAT, tuple.elements()));
    };
  }
}
