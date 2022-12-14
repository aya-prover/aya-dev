// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.visitor;

import kala.value.MutableValue;
import org.aya.core.pat.Pat;
import org.aya.generic.util.InternalException;
import org.aya.ref.LocalVar;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;

import java.util.function.UnaryOperator;

public interface PatTraversal extends UnaryOperator<Pat> {
  default @NotNull Pat pre(@NotNull Pat pat) {
    return pat;
  }

  default @NotNull Pat post(@NotNull Pat pat) {
    return pat;
  }

  @Override
  default @NotNull Pat apply(@NotNull Pat pat) {
    return post(descent(pre(pat)));
  }

  default @NotNull Pat descent(@NotNull Pat pat) {
    return switch (pat) {
      case Pat.Absurd absurd -> absurd;
      case Pat.Bind bind -> bind;
      case Pat.Ctor ctor -> {
        var params = ctor.params().map(x -> x.descent(this));

        if (params.sameElements(ctor.params(), true)) yield ctor;
        yield new Pat.Ctor(ctor.ref(), params, ctor.type());
      }
      case Pat.Meta meta -> {
        var solution = meta.solution().get();
        if (solution != null) {
          var newSolution = apply(solution);

          if (newSolution == solution) yield meta;
          yield new Pat.Meta(MutableValue.create(solution), meta.fakeBind(), meta.type());
        } else {
          yield meta;
        }
      }
      case Pat.ShapedInt shapedInt -> shapedInt;
      case Pat.Tuple tuple -> {
        var pats = tuple.pats().map(this);

        if (pats.sameElements(tuple.pats(), true)) yield tuple;
        yield new Pat.Tuple(pats);
      }
    };
  }

  /**
   * A traversal that disallow Pat.Meta
   */
  interface NoMeta extends PatTraversal {
    @Override
    @NotNull
    default Pat descent(@NotNull Pat pat) {
      if (pat instanceof Pat.Meta) {
        throw new InternalException("expected: no Pat.Meta, but actual: Pat.Meta");
      }

      return PatTraversal.super.descent(pat);
    }
  }

  /**
   * subst all binding to corresponding MetaPat
   * <p>
   * TODO[hoshino]: A PatTraversal or a method of Pat?
   */
  record MetaBind(@NotNull Subst subst, @NotNull SourcePos definition) implements NoMeta {
    @Override public @NotNull Pat post(@NotNull Pat pat) {
      if (pat instanceof Pat.Bind(var bind, var type)) {
        // every new var use the same definition location
        var newVar = new LocalVar(bind.name(), definition);
        // we are no need to add newVar to some localCtx, this will be done when we inline the Pat.Meta
        var meta = new Pat.Meta(MutableValue.create(), newVar, type);

        // add to subst
        subst.addDirectly(bind, meta.toTerm());

        return meta;
      }

      return NoMeta.super.post(pat);
    }
  }
}
