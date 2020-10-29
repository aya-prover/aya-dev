// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.ref;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.mzi.api.ref.Var;

/**
 * @author ice1000
 */
public record DefVar<Def>(@NotNull Def def, @NotNull String name) implements Var {
  @Contract("_, _ -> new") public static <T> @NotNull DefVar<T> cast(
    @NotNull Class<? extends T> klass, @NotNull Var obj
  ) throws ClassCastException {
    if (obj instanceof DefVar<?> defVar) return cast(klass, defVar);
    else throw new ClassCastException(obj.getClass() + " " + obj.name());
  }

  @Contract("_, _ -> new") public static <T> @NotNull DefVar<T> cast(
    @NotNull Class<? extends T> klass, @NotNull DefVar<?> obj
  ) throws ClassCastException {
    return new DefVar<>(klass.cast(obj.def), obj.name());
  }
}
