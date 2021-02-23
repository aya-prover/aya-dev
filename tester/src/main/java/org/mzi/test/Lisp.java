// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.test;

import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;
import org.mzi.api.ref.DefVar;
import org.mzi.api.ref.Var;
import org.mzi.concrete.Decl;
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
  static @NotNull Term parse(@NotNull @NonNls @Language("TEXT") String code) {
    return parse(code, new HashMap<>());
  }

  static @NotNull Term parse(@NotNull @NonNls @Language("TEXT") String code, @NotNull Map<String, @NotNull Var> refs) {
    return Objects.requireNonNull(TermDsl.parse(code, refs));
  }

  @SuppressWarnings("unchecked")
  static @NotNull FnDef parseDef(
    @NotNull @NonNls String name,
    @NotNull @NonNls @Language("TEXT") String teleCode,
    @NotNull @NonNls @Language("TEXT") String resultTypeCode,
    @NotNull @NonNls @Language("TEXT") String bodyCode,
    @NotNull Map<String, @NotNull Var> refs) {
    var tele = parseTele(teleCode, refs);
    var result = parse(resultTypeCode, refs);
    var body = parse(bodyCode, refs);
    var existingRef = (DefVar<FnDef, Decl.FnDecl>) refs.getOrDefault(name, DefVar.core(null, name));
    var def = new FnDef(existingRef, tele, result, body);
    var ref = def.ref();
    refs.put(name, ref);
    return def;
  }

  static @NotNull ImmutableSeq<Term.@NotNull Param> parseTele(@NotNull @NonNls @Language("TEXT") String code, @NotNull Map<String, @NotNull Var> refs) {
    return Objects.requireNonNull(TermDsl.parseTele(code, refs));
  }
}
