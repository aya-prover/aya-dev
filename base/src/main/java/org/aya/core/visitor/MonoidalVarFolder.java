// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.visitor;

import kala.collection.SeqLike;
import kala.collection.SeqView;
import org.aya.core.def.*;
import org.aya.core.term.*;
import org.aya.generic.Arg;
import org.aya.guest0x0.cubical.Partial;
import org.aya.ref.AnyVar;
import org.aya.ref.DefVar;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

public interface MonoidalVarFolder<R> extends Function<Term, R> {
  // TODO: Do we need to visit variables in `access` and `new` as well?
  @NotNull R var(@NotNull AnyVar var);

  @NotNull R merge(@NotNull SeqView<R> rs);

  private @NotNull SeqView<R> trace(@NotNull Term term) {
    return switch (term) {
      case FormTerm.Pi pi -> trace(pi.param()).concat(trace(pi.body()));
      case FormTerm.Sigma sigma -> sigma.params().view().flatMap(this::trace);
      case IntroTerm.Lambda lambda -> trace(lambda.param()).concat(trace(lambda.body()));
      case IntroTerm.Tuple tuple -> tuple.items().view().flatMap(this::trace);
      case IntroTerm.New nevv ->
        trace(nevv.struct()).concat(nevv.params().valuesView().map(this::trace).fold(SeqView.empty(), SeqView::concat));
      case ElimTerm.App app -> trace(app.of()).concat(trace(app.arg()));
      case ElimTerm.Proj proj -> trace(proj.of());
      case CallTerm.Struct struct -> struct.args().view().flatMap(this::trace).prepended(var(struct.ref()));
      case CallTerm.Data data -> data.args().view().flatMap(this::trace).prepended(var(data.ref()));
      case CallTerm.Con con -> con.head().dataArgs().view().flatMap(this::trace)
        .concat(con.args().view().flatMap(this::trace))
        .prepended(var(con.ref()));
      case CallTerm.Fn fn -> fn.args().view().flatMap(this::trace).prepended(var(fn.ref()));
      case CallTerm.Access access ->
        access.structArgs().view().flatMap(this::trace).concat(access.fieldArgs().view().flatMap(this::trace)).concat(trace(access.of()));
      case CallTerm.Prim prim -> prim.args().view().flatMap(this::trace).prepended(var(prim.ref()));
      case CallTerm.Hole hole -> hole.args().view().flatMap(this::trace)
        .concat(hole.contextArgs().view().flatMap(this::trace))
        .prepended(var(hole.ref()));
      case LitTerm.ShapedInt shaped -> trace(shaped.type());
      case RefTerm ref -> SeqView.of(var(ref.var()));
      case RefTerm.Field field -> SeqView.of(var(field.ref()));
      case FormTerm.PartTy ty -> trace(ty.type()).concat(ty.restr().instView().flatMap(this::trace));
      case IntroTerm.PartEl el -> trace(el.partial());
      case PrimTerm.Mula mula -> mula.view().flatMap(this::trace);
      case FormTerm.Path path -> trace(path.cube().type()).concat(trace(path.cube().partial()));
      case IntroTerm.PathLam lam -> trace(lam.body());
      case ElimTerm.PathApp app -> trace(app.of()).concat(app.args().flatMap(this::trace));
      default -> SeqView.empty();
    };
  }
  @NotNull private SeqView<R> trace(@NotNull Partial<Term> partial) {
    return switch (partial) {
      case Partial.Split<Term> hap ->
        hap.clauses().view().flatMap(c -> c.cof().view().flatMap(this::trace).concat(trace(c.u())));
      case Partial.Const<Term> sad -> trace(sad.u());
    };
  }
  private @NotNull SeqView<R> trace(@NotNull Term.Param param) {
    return trace(param.type());
  }
  private @NotNull SeqView<R> trace(@NotNull Arg<Term> arg) {
    return trace(arg.term());
  }

  @Override default @NotNull R apply(@NotNull Term term) {
    return merge(trace(term));
  }

  record Usages(@NotNull AnyVar var) implements MonoidalVarFolder<Integer> {
    @Override public @NotNull Integer var(@NotNull AnyVar v) {
      return v == var ? 1 : 0;
    }

    @Override public @NotNull Integer merge(@NotNull SeqView<Integer> integers) {
      return integers.fold(0, Integer::sum);
    }
  }

  /**
   * @author re-xyr, ice1000
   * @see RefFinder#HEADER_ONLY
   * @see RefFinder#HEADER_AND_BODY
   */
  record RefFinder(boolean withBody) implements
    MonoidalVarFolder<@NotNull SeqView<Def>> {
    public static final @NotNull MonoidalVarFolder.RefFinder HEADER_ONLY = new RefFinder(false);
    public static final @NotNull MonoidalVarFolder.RefFinder HEADER_AND_BODY = new RefFinder(true);

    @Override public @NotNull SeqView<Def> var(@NotNull AnyVar usage) {
      return usage instanceof DefVar<?, ?> ref && ref.core instanceof Def def ? SeqView.of(def) : SeqView.empty();
    }

    @Override public @NotNull SeqView<Def> merge(@NotNull SeqView<SeqView<Def>> as) {
      return as.flatMap(a -> a);
    }

    public @NotNull SeqView<Def> apply(@NotNull GenericDef def) {
      return switch (def) {
        case FnDef fn -> tele(fn.telescope)
          .concat(apply(fn.result))
          .concat(withBody
            ? fn.body.fold(this, clauses -> clauses.view().flatMap(this::matchy))
            : SeqView.empty());
        case CtorDef ctor ->
          tele(ctor.selfTele).concat(withBody ? ctor.clauses.flatMap(this::matchy) : SeqView.empty());
        case StructDef struct ->
          tele(struct.telescope).concat(withBody ? struct.fields.flatMap(this::apply) : SeqView.empty());
        case FieldDef field -> tele(field.telescope())
          .concat(field.body.map(this).getOrDefault(SeqView.empty()))
          .concat(apply(field.result));
        case PrimDef prim -> tele(prim.telescope);
        case DataDef data -> tele(data.telescope)
          .concat(apply(data.result))
          .concat(withBody ? data.body.flatMap(this::apply) : SeqView.empty());
        default -> SeqView.empty();
      };
    }

    private SeqView<Def> matchy(@NotNull Term.Matching match) {
      return apply(match.body());
    }

    private SeqView<Def> tele(@NotNull SeqLike<Term.Param> telescope) {
      return telescope.view().map(Term.Param::type).flatMap(this);
    }
  }
}
