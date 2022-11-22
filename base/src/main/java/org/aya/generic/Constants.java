// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.generic;

import org.aya.concrete.Expr;
import org.aya.ref.LocalVar;
import org.aya.util.error.Global;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Pattern;

public interface Constants {
  @NotNull @NonNls String ANONYMOUS_PREFIX = "_";
  @NotNull @NonNls String GENERATED_POSTFIX = "'";
  @NotNull @NonNls String SCOPE_SEPARATOR = "::";
  @NotNull Pattern SCOPE_SEPARATOR_PATTERN = Pattern.compile(SCOPE_SEPARATOR);
  @NotNull @NonNls String AYA_POSTFIX = ".aya";
  @NotNull @NonNls String AYA_LITERATE_POSTFIX = ".aya.md"; // TODO: better name like `.laya`
  @NotNull Pattern AYA_POSTFIX_PATTERN = Pattern.compile("(\\.aya$)|(\\.aya\\.md$)");
  @NotNull @NonNls String AYAC_POSTFIX = ".ayac";
  @NotNull @NonNls String AYA_JSON = "aya.json";

  @NotNull @NonNls String ALTERNATIVE_EMPTY = "empty";
  @NotNull @NonNls String ALTERNATIVE_OR = "<|>";
  @NotNull @NonNls String LIST_NIL = "nil";
  @NotNull @NonNls String LIST_CONS = ":<";
  @NotNull @NonNls String APPLICATIVE_APP = "<*>";
  @NotNull @NonNls String FUNCTOR_PURE = "pure";
  @NotNull @NonNls String MONAD_BIND = ">>=";

  static @NotNull Expr alternativeOr(@NotNull SourcePos pos) {
    return new Expr.Unresolved(pos, ALTERNATIVE_OR);
  }
  static @NotNull Expr alternativeEmpty(@NotNull SourcePos pos) {
    return new Expr.Unresolved(pos, ALTERNATIVE_EMPTY);
  }
  static @NotNull Expr listNil(@NotNull SourcePos pos) {
    return new Expr.Unresolved(pos, LIST_NIL);
  }
  static @NotNull Expr listCons(@NotNull SourcePos pos) {
    return new Expr.Unresolved(pos, LIST_CONS);
  }
  static @NotNull Expr applicativeApp(@NotNull SourcePos pos) {
    return new Expr.Unresolved(pos, APPLICATIVE_APP);
  }
  static @NotNull Expr functorPure(@NotNull SourcePos pos) {
    return new Expr.Unresolved(pos, FUNCTOR_PURE);
  }
  static @NotNull Expr monadBind(@NotNull SourcePos pos) {
    return new Expr.Unresolved(pos, MONAD_BIND);
  }

  static @NotNull LocalVar anonymous() {
    return new LocalVar(ANONYMOUS_PREFIX, SourcePos.NONE);
  }
  static @NotNull LocalVar randomlyNamed(@NotNull SourcePos pos) {
    return new LocalVar(randomName(pos), pos, true);
  }
  static @NotNull String randomName(@NotNull Object pos) {
    if (Global.NO_RANDOM_NAME) return ANONYMOUS_PREFIX;
    return ANONYMOUS_PREFIX + Math.abs(pos.hashCode()) % 10;
  }
}
