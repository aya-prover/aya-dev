// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler;

import com.intellij.openapi.util.text.StringUtil;
import kala.collection.SeqLike;
import kala.collection.immutable.ImmutableSeq;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;

public interface ExprializeUtils {
  String SEP = ", ";

  static @NotNull String makeNew(@NotNull String className, String... terms) {
    return ImmutableSeq.from(terms).joinToString(SEP, "new " + className + "(", ")");
  }

  static @NotNull String makeImmutableSeq(
    @NotNull String typeName, @NotNull ImmutableSeq<String> terms, @NotNull String seqName
  ) {
    if (terms.isEmpty()) {
      return seqName + ".empty()";
    } else {
      return terms.joinToString(SEP, seqName + ".<" + typeName + ">of(", ")");
    }
  }

  static @NotNull String makeImmutableSeq(@NotNull String typeName, @NotNull ImmutableSeq<String> terms) {
    return makeImmutableSeq(typeName, terms, AyaSerializer.CLASS_IMMSEQ);
  }

  static @NotNull String makeThunk(@NotNull String value) {
    return "() -> " + value;
  }

  static @NotNull String makeArrayFrom(@NotNull String type, @NotNull ImmutableSeq<String> elements) {
    return "new " + type + "[] " + makeHalfArrayFrom(elements);
  }

  static @NotNull String makeHalfArrayFrom(@NotNull SeqLike<String> elements) {
    return elements.joinToString(", ", "{ ", " }");
  }

  static @NotNull String makeSub(@NotNull String superClass, @NotNull String sub) {
    return superClass + "." + sub;
  }

  static @NotNull String makeEnum(@NotNull String enumClass, @NotNull Enum<?> value) {
    return makeSub(enumClass, value.toString());
  }

  static @NotNull String makeString(@NotNull String raw) {
    return "\"" + StringUtil.escapeStringCharacters(raw) + "\"";
  }

  static @NotNull String isNull(@NotNull String term) {
    return term + " == null";
  }

  @NotNull String SOURCE_POS_SER = makeSub(getJavaRef(SourcePos.class), "SER");

  static @NotNull String getInstance(@NotNull String defName) {
    return defName + "." + AyaSerializer.STATIC_FIELD_INSTANCE;
  }

  static @NotNull String getCallInstance(@NotNull String term) {
    return term + "." + AyaSerializer.FIELD_INSTANCE + "()";
  }

  static @NotNull String getEmptyCallTerm(@NotNull String term) {
    return term + "." + AyaSerializer.FIELD_EMPTYCALL;
  }

  /**
   * Get the reference to {@param clazz}, it should be imported to current file.
   */
  static @NotNull String getJavaRef(@NotNull Class<?> clazz) {
    return clazz.getSimpleName().replace('$', '.');
  }
}
