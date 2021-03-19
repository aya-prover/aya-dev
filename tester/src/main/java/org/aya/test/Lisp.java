// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.test;

import org.aya.api.ref.DefVar;
import org.aya.api.ref.Var;
import org.aya.concrete.Decl;
import org.aya.core.TermDsl;
import org.aya.core.def.FnDef;
import org.aya.core.term.Term;
import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.glavo.kala.collection.mutable.MutableMap;
import org.glavo.kala.control.Either;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.util.Objects;

/**
 * @author ice1000
 */
@TestOnly
public interface Lisp {
  static @NotNull Term parse(@NotNull @NonNls @Language("TEXT") String code) {
    return parse(code, MutableMap.create());
  }

  static @NotNull Term parse(@NotNull @NonNls @Language("TEXT") String code, @NotNull MutableMap<String, @NotNull Var> refs) {
    return Objects.requireNonNull(TermDsl.parse(code, refs));
  }

  @SuppressWarnings("unchecked")
  static @NotNull FnDef parseDef(
    @NotNull @NonNls String name,
    @NotNull @NonNls @Language("TEXT") String teleCode,
    @NotNull @NonNls @Language("TEXT") String resultTypeCode,
    @NotNull @NonNls @Language("TEXT") String bodyCode,
    @NotNull MutableMap<String, @NotNull Var> refs) {
    var tele = parseTele(teleCode, refs);
    var result = parse(resultTypeCode, refs);
    var body = parse(bodyCode, refs);
    var existingRef = (DefVar<FnDef, Decl.FnDecl>) refs.getOrDefault(name, DefVar.core(null, name));
    var def = new FnDef(existingRef, ImmutableSeq.of(), tele, result, Either.left(body));
    var ref = def.ref();
    refs.put(name, ref);
    return def;
  }

  static @NotNull ImmutableSeq<Term.@NotNull Param> parseTele(@NotNull @NonNls @Language("TEXT") String code, @NotNull MutableMap<String, @NotNull Var> refs) {
    return Objects.requireNonNull(TermDsl.parseTele(code, refs));
  }
}
