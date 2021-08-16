// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.serde;

import kala.collection.Seq;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableMap;
import kala.collection.mutable.MutableTreeMap;
import org.aya.api.ref.DefVar;
import org.aya.api.ref.LocalVar;
import org.aya.api.util.Arg;
import org.aya.concrete.stmt.Decl;
import org.aya.core.def.Def;
import org.aya.core.sort.Sort;
import org.aya.core.term.*;
import org.aya.util.Constants;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;

/**
 * @author ice1000
 */
public sealed interface SerTerm extends Serializable {
  record DeState(
    @NotNull MutableMap<Seq<String>, MutableMap<String, DefVar<?, ?>>> defCache,
    @NotNull MutableMap<Integer, Sort.LvlVar> levelCache,
    @NotNull MutableMap<Integer, LocalVar> localCache
  ) {
    public @NotNull LocalVar var(int var) {
      return localCache.getOrPut(var, () -> new LocalVar(Constants.ANONYMOUS_PREFIX));
    }

    public @NotNull DefVar<?, ?> def(@NotNull ImmutableSeq<String> mod, @NotNull String name) {
      return defCache.getOrPut(mod, MutableTreeMap::new).getOrPut(name,
        () -> DefVar.<Def, Decl>core(null, name));
    }
  }

  @NotNull Term de(@NotNull DeState state);

  record SerParam(boolean explicit, int var, @NotNull SerTerm term) implements Serializable {
    public @NotNull Term.Param de(@NotNull DeState state) {
      return new Term.Param(state.var(var), term.de(state), explicit);
    }
  }

  record Pi(@NotNull SerTerm.SerParam param, @NotNull SerTerm body) implements SerTerm {
    @Override public @NotNull Term de(@NotNull DeState state) {
      return new FormTerm.Pi(param.de(state), body.de(state));
    }
  }

  record Sigma(@NotNull ImmutableSeq<SerParam> params) implements SerTerm {
    @Override public @NotNull Term de(@NotNull DeState state) {
      return new FormTerm.Sigma(params.map(p -> p.de(state)));
    }
  }

  record Univ(@NotNull SerLevel.Max u, @NotNull SerLevel.Max h) implements SerTerm {
    @Override public @NotNull Term de(@NotNull DeState state) {
      return new FormTerm.Univ(new Sort(u.de(state.levelCache), h.de(state.levelCache)));
    }
  }

  record Ref(int var, @NotNull SerTerm type) implements SerTerm {
    @Override public @NotNull Term de(@NotNull DeState state) {
      return new RefTerm(state.var(var), type.de(state));
    }
  }

  record Lam(@NotNull SerParam param, @NotNull SerTerm body) implements SerTerm {
    @Override public @NotNull Term de(@NotNull DeState state) {
      return new IntroTerm.Lambda(param.de(state), body.de(state));
    }
  }

  // TODO
  record New(@NotNull StructCall call) implements SerTerm {
    @Override public @NotNull Term de(@NotNull DeState state) {
      throw new UnsupportedOperationException("TODO");
    }
  }

  record Proj(@NotNull SerTerm of, int ix) implements SerTerm {
    @Override public @NotNull Term de(@NotNull DeState state) {
      return new ElimTerm.Proj(of.de(state), ix);
    }
  }

  record App(@NotNull SerTerm of, @NotNull SerTerm arg, boolean explicit) implements SerTerm {
    @Override public @NotNull Term de(@NotNull DeState state) {
      return new ElimTerm.App(of.de(state), new Arg<>(arg.de(state), explicit));
    }
  }

  // TODO
  record StructCall() implements SerTerm {
    @Override public @NotNull Term de(@NotNull DeState state) {
      throw new UnsupportedOperationException();
    }
  }

  record Tup(@NotNull ImmutableSeq<SerTerm> components) implements SerTerm {
    @Override public @NotNull Term de(@NotNull DeState state) {
      return new IntroTerm.Tuple(components.map(t -> t.de(state)));
    }
  }
}
