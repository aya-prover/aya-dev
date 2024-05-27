// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.compile;

import kala.collection.Seq;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableArrayList;
import kala.control.Result;
import org.aya.syntax.core.def.ConDefLike;
import org.aya.syntax.core.def.DataDefLike;
import org.aya.syntax.core.term.FreeTerm;
import org.aya.syntax.core.term.Param;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.ref.GenerateKind;
import org.aya.syntax.ref.LocalVar;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;

public abstract non-sealed class JitCon extends JitDef implements ConDefLike {
  public final JitData dataType;
  private final boolean hasEq;

  protected JitCon(int telescopeSize, boolean[] telescopeLicit, String[] telescopeName, JitData dataType, boolean hasEq) {
    super(telescopeSize, telescopeLicit, telescopeName);
    this.dataType = dataType;
    this.hasEq = hasEq;
  }

  /**
   * Whether this constructor is available of data type
   *
   * @param args the argument to the data type
   * @return a match result, a sequence of substitution if success
   */
  public abstract @NotNull Result<ImmutableSeq<Term>, Boolean> isAvailable(@NotNull Seq<Term> args);

  @Override
  public boolean hasEq() {
    return hasEq;
  }

  @Override public abstract @NotNull Term equality(Seq<Term> args, boolean is0);

  @Override public @NotNull DataDefLike dataRef() { return dataType; }

  @Override public @NotNull ImmutableSeq<Param> selfTele(@NotNull ImmutableSeq<Term> ownerArgs) {
    var ownerArgsSize = ownerArgs.size();
    var selfArgsSize = telescopeSize - ownerArgsSize;
    var args = MutableArrayList.<Term>create(telescopeSize);
    args.appendAll(ownerArgs);
    var tele = MutableArrayList.<Param>create(selfArgsSize);

    for (var i = 0; i < selfArgsSize; ++i) {
      var realIdx = ownerArgsSize + i;
      var name = telescopeNames[realIdx];
      var licit = telescopeLicit[realIdx];
      var type = telescope(realIdx, args).instantiateTele(args.view());
      var bind = new LocalVar(name, SourcePos.NONE, GenerateKind.Basic.Tyck);
      args.append(new FreeTerm(bind));
      tele.append(new Param(name, type, licit));
    }

    return tele.toImmutableSeq();
  }
}
