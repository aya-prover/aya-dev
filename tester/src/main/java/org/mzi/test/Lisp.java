// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.test;

import asia.kala.collection.immutable.ImmutableSeq;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.mzi.api.ref.Var;
import org.mzi.core.Param;
import org.mzi.core.TermDsl;
import org.mzi.core.def.FnDef;
import org.mzi.core.term.Term;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * @author ice1000
 */
@TestOnly
public interface Lisp {
  static @Nullable Term somehowParse(@NotNull @NonNls @Language("TEXT") String code, @NotNull Map<String, @NotNull Var> refs) {
    return TermDsl.parse(code, refs);
  }

  static @NotNull Term reallyParse(@NotNull @NonNls @Language("TEXT") String code) {
    return reallyParse(code, new HashMap<>());
  }

  static @NotNull Term reallyParse(@NotNull @NonNls @Language("TEXT") String code, @NotNull Map<String, @NotNull Var> refs) {
    return Objects.requireNonNull(somehowParse(code, refs));
  }

  static @Nullable ImmutableSeq<@NotNull Param> somehowParseTele(@NotNull @NonNls @Language("TEXT") String code, @NotNull Map<String, @NotNull Var> refs) {
    return TermDsl.parseTele(code, refs);
  }

  static @NotNull FnDef reallyParseDef(
    @NotNull @NonNls String name,
    @NotNull @NonNls @Language("TEXT") String teleCode,
    @NotNull @NonNls @Language("TEXT") String resultTypeCode,
    @NotNull @NonNls @Language("TEXT") String bodyCode,
    @NotNull Map<String, @NotNull Var> refs) {
    var tele = reallyParseTele(teleCode, refs);
    var result = reallyParse(resultTypeCode, refs);
    var body = reallyParse(bodyCode, refs);
    var def = new FnDef(name, tele, result, body);
    var ref = def.ref();
    refs.put(name, ref);
    return def;
  }

  static @NotNull ImmutableSeq<@NotNull Param> reallyParseTele(@NotNull @NonNls @Language("TEXT") String code, @NotNull Map<String, @NotNull Var> refs) {
    return Objects.requireNonNull(somehowParseTele(code, refs));
  }
}
