// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.repr;

import org.jetbrains.annotations.NotNull;

/**
 * @author kiva
 */
sealed public interface ParamShape {
  enum Any implements ParamShape {
    INSTANCE;
  }

  record Licit(
    @NotNull CodeShape.LocalId name,
    @NotNull TermShape type,
    Licit.Kind kind
  ) implements ParamShape, CodeShape.Moment {
    enum Kind {
      Any, Ex, Im
    }
  }

  static @NotNull ParamShape explicit(@NotNull TermShape type) {
    return explicit(CodeShape.LocalId.IGNORED, type);
  }

  static @NotNull ParamShape explicit(@NotNull CodeShape.LocalId name, @NotNull TermShape type) {
    return new Licit(name, type, Licit.Kind.Ex);
  }

  static @NotNull ParamShape implicit(@NotNull TermShape type) {
    return new Licit(CodeShape.LocalId.IGNORED, type, Licit.Kind.Im);
  }

  static @NotNull ParamShape anyLicit(@NotNull CodeShape.LocalId name, @NotNull TermShape type) {
    return new Licit(name, type, Licit.Kind.Any);
  }

  static @NotNull ParamShape anyLicit(@NotNull TermShape type) {
    return anyLicit(CodeShape.LocalId.IGNORED, type);
  }

  static @NotNull ParamShape anyEx() {
    return explicit(TermShape.Any.INSTANCE);
  }

  static @NotNull ParamShape anyIm() {
    return implicit(TermShape.Any.INSTANCE);
  }

  // anyLicit(TermShape.Any) would be equivalent to ParamShape.Any
}
