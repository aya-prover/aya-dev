// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler;

import com.intellij.openapi.util.text.StringUtil;
import kala.collection.SeqLike;
import kala.collection.immutable.ImmutableSeq;
import org.jetbrains.annotations.NotNull;

public interface ExprializeUtils {
  String SEP = ", ";

  static @NotNull String makeNew(@NotNull String className, String... terms) {
    return ImmutableSeq.from(terms).joinToString(SEP, STR."new \{className}(", ")");
  }

  static @NotNull String makeImmutableSeq(
    @NotNull String typeName, @NotNull ImmutableSeq<String> terms, @NotNull String seqName
  ) {
    if (terms.isEmpty()) {
      return STR."\{seqName}.empty()";
    } else {
      return terms.joinToString(SEP, STR."\{seqName}.<\{typeName}>of(", ")");
    }
  }

  static @NotNull String makeImmutableSeq(@NotNull String typeName, @NotNull ImmutableSeq<String> terms) {
    return makeImmutableSeq(typeName, terms, AyaSerializer.CLASS_IMMSEQ);
  }

  static @NotNull String makeThunk(@NotNull String value) {
    return STR."() -> \{value}";
  }

  static @NotNull String makeArrayFrom(@NotNull String type, @NotNull ImmutableSeq<String> elements) {
    return STR."new \{type}[] \{makeHalfArrayFrom(elements)}";
  }

  static @NotNull String makeHalfArrayFrom(@NotNull SeqLike<String> elements) {
    return elements.joinToString(", ", "{ ", " }");
  }

  static @NotNull String makeSub(@NotNull String superClass, @NotNull String sub) {
    return STR."\{superClass}.\{sub}";
  }

  static @NotNull String makeString(@NotNull String raw) {
    return STR."\"\{StringUtil.escapeStringCharacters(raw)}\"";
  }

  static @NotNull String isNull(@NotNull String term) {
    return STR."\{term} == null";
  }

  static @NotNull String getInstance(@NotNull String defName) {
    return STR."\{defName}.\{AyaSerializer.STATIC_FIELD_INSTANCE}";
  }

  static @NotNull String getCallInstance(@NotNull String term) {
    return STR."\{term}.\{AyaSerializer.FIELD_INSTANCE}()";
  }

  /**
   * Get the reference to {@param clazz}, it should be imported to current file.
   */
  static @NotNull String getJavaReference(@NotNull Class<?> clazz) {
    return clazz.getSimpleName().replace('$', '.');
  }
}
