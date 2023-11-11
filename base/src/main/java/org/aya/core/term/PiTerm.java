// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

import kala.collection.Seq;
import kala.collection.SeqLike;
import kala.collection.mutable.MutableList;
import org.aya.core.pat.Pat;
import org.aya.generic.SortKind;
import org.aya.ref.LocalVar;
import org.aya.tyck.Result;
import org.aya.util.Arg;
import org.jetbrains.annotations.NotNull;

import java.util.function.UnaryOperator;

/**
 * @author re-xyr, kiva, ice1000
 */
public record PiTerm(@NotNull Param param, @NotNull Term body) implements StableWHNF, Formation {
  public @NotNull PiTerm update(@NotNull Param param, @NotNull Term body) {
    return param == param() && body == body() ? this : new PiTerm(param, body);
  }

  @Override public @NotNull PiTerm descent(@NotNull UnaryOperator<Term> f, @NotNull UnaryOperator<Pat> g) {
    return update(param.descent(f), f.apply(body));
  }

  public static @NotNull Term unpi(@NotNull Term term, @NotNull UnaryOperator<Term> fmap, @NotNull MutableList<Param> params) {
    if (fmap.apply(term) instanceof PiTerm(var param, var body)) {
      params.append(param);
      return unpi(body, fmap, params);
    } else return term;
  }

  /**
   * @param fmap   usually whnf or identity
   * @param params will be of size unequal to limit in case of failure
   */
  public static @NotNull Result.Default unpiOrPath(
    @NotNull Term ty, @NotNull Term term, @NotNull UnaryOperator<Term> fmap,
    @NotNull MutableList<LocalVar> params, int limit
  ) {
    if (limit <= 0) return new Result.Default(term, ty);
    return switch (fmap.apply(ty)) {
      case PiTerm(var param, var body) when param.explicit() -> {
        if (param.type() != IntervalTerm.INSTANCE) yield new Result.Default(term, ty);
        params.append(param.ref());
        yield unpiOrPath(body, AppTerm.make(term, param.toArg()), fmap, params, limit - 1);
      }
      case PathTerm cube -> {
        var cubeParams = cube.params();
        int delta = limit - cubeParams.size();
        if (delta >= 0) {
          params.appendAll(cubeParams);
          yield unpiOrPath(cube.type(), cube.applyDimsTo(term), fmap, params, delta);
        } else {
          throw new UnsupportedOperationException("TODO");
        }
      }
      case Term anyway -> new Result.Default(term, anyway);
    };
  }

  public static @NotNull SortTerm lub(@NotNull SortTerm domain, @NotNull SortTerm codomain) {
    var alift = domain.lift();
    var blift = codomain.lift();
    return switch (domain.kind()) {
      case Type -> switch (codomain.kind()) {
        case Type, Set -> new SortTerm(SortKind.Type, Math.max(alift, blift));
        case ISet -> new SortTerm(SortKind.Set, alift);
      };
      case ISet -> switch (codomain.kind()) {
        case ISet -> SortTerm.Set0;
        case Set, Type -> codomain;
      };
      case Set -> switch (codomain.kind()) {
        case Set, Type -> new SortTerm(SortKind.Set, Math.max(alift, blift));
        case ISet -> new SortTerm(SortKind.Set, alift);
      };
    };
  }

  public static Term makeIntervals(Seq<LocalVar> list, Term type) {
    return make(list.view().map(IntervalTerm::param), type);
  }

  public @NotNull LamTerm coe(@NotNull CoeTerm coe, @NotNull LamTerm.Param varI) {
    var M = new LamTerm.Param(new LocalVar("f"), true);
    var a = new LamTerm.Param(new LocalVar("a"), param.explicit());
    var arg = AppTerm.make(coe.inverse(new LamTerm(varI, param.type()).rename()), new Arg<>(a.toTerm(), true));
    var cover = CoeTerm.cover(varI, param, body, a.toTerm(), coe.s()).rename();
    return new LamTerm(M, new LamTerm(a,
      AppTerm.make(coe.recoe(cover),
        new Arg<>(AppTerm.make(M.toTerm(), new Arg<>(arg, param.explicit())), true))));
  }

  public @NotNull Term substBody(@NotNull Term term) {
    return body.subst(param.ref(), term);
  }

  public @NotNull Term parameters(@NotNull MutableList<@NotNull Param> params) {
    params.append(param);
    var t = body;
    while (t instanceof PiTerm(var p, var b)) {
      params.append(p);
      t = b;
    }
    return t;
  }

  public static @NotNull Term make(@NotNull SeqLike<@NotNull Param> telescope, @NotNull Term body) {
    return telescope.view().foldRight(body, PiTerm::new);
  }
}
