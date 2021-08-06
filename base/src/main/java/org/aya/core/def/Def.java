// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.def;

import kala.collection.SeqLike;
import kala.collection.immutable.ImmutableSeq;
import kala.tuple.Unit;
import org.aya.api.core.CoreDef;
import org.aya.api.ref.DefVar;
import org.aya.concrete.stmt.Signatured;
import org.aya.core.sort.Sort;
import org.aya.core.term.Term;
import org.aya.core.visitor.Substituter;
import org.aya.distill.CoreDistiller;
import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Docile;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * @author ice1000
 */
public sealed interface Def extends CoreDef, TopLevel
  permits DataDef, DataDef.Ctor, FnDef, PrimDef, StructDef, StructDef.Field {
  static @NotNull ImmutableSeq<Term.Param> defTele(@NotNull DefVar<? extends Def, ? extends Signatured> defVar) {
    if (defVar.core != null) return defVar.core.telescope();
      // guaranteed as this is already a core term
    else return Objects.requireNonNull(defVar.concrete.signature).param;
  }
  static @NotNull ImmutableSeq<Sort.LvlVar> defLevels(@NotNull DefVar<? extends Def, ? extends Signatured> defVar) {
    var core = defVar.core;
    if (core instanceof DataDef data) return data.levels();
    else if (core instanceof FnDef fn) return fn.levels();
    else if (core instanceof StructDef struct) return struct.levels();
    else if (core instanceof DataDef.Ctor ctor) return defLevels(ctor.dataRef());
    else if (core instanceof StructDef.Field field) return defLevels(field.structRef());
    else if (core instanceof PrimDef prim) return prim.levels();
      // guaranteed as this is already a core term
    else return Objects.requireNonNull(defVar.concrete.signature).sortParam();
  }
  static @NotNull Term defResult(@NotNull DefVar<? extends Def, ? extends Signatured> defVar) {
    if (defVar.core != null) return defVar.core.result();
      // guaranteed as this is already a core term
    else return Objects.requireNonNull(defVar.concrete.signature).result;
  }
  static @NotNull ImmutableSeq<Term.Param>
  substParams(@NotNull SeqLike<Term.@NotNull Param> param, Substituter.@NotNull TermSubst subst) {
    return Term.Param.subst(param.view().drop(1), subst);
  }

  @Override @NotNull Term result();
  @Override @NotNull DefVar<? extends Def, ? extends Signatured> ref();
  @Override @NotNull ImmutableSeq<Term.Param> telescope();

  <P, R> R accept(@NotNull Visitor<P, R> visitor, P p);
  @Override default @NotNull Doc toDoc() {
    return accept(CoreDistiller.INSTANCE, Unit.unit());
  }

  /**
   * @author re-xyr
   */
  interface Visitor<P, R> {
    R visitFn(@NotNull FnDef def, P p);
    R visitData(@NotNull DataDef def, P p);
    R visitCtor(@NotNull DataDef.Ctor def, P p);
    R visitStruct(@NotNull StructDef def, P p);
    R visitField(@NotNull StructDef.Field def, P p);
    R visitPrim(@NotNull PrimDef def, P p);
  }

  /**
   * Signature of a definition, used in concrete and tycking.
   *
   * @author ice1000
   */
  record Signature(
    @NotNull ImmutableSeq<Sort.@NotNull LvlVar> sortParam,
    @NotNull ImmutableSeq<Term.@NotNull Param> param,
    @NotNull Term result
  ) implements Docile {
    @Contract("_ -> new") public @NotNull Signature inst(@NotNull Term term) {
      var subst = new Substituter.TermSubst(param.first().ref(), term);
      return new Signature(sortParam, substParams(param, subst), result.subst(subst));
    }

    @Override public @NotNull Doc toDoc() {
      return Doc.cat(Doc.join(Doc.ONE_WS, param.view().map(Term.Param::toDoc)), Doc.plain(" -> "), result.toDoc());
    }

    @Contract("_ -> new") public @NotNull Signature mapTerm(@NotNull Term term) {
      return new Signature(sortParam, param, term);
    }

    public @NotNull Signature subst(@NotNull Substituter.TermSubst subst) {
      return new Signature(sortParam, Term.Param.subst(param, subst), result.subst(subst));
    }
  }
}
