package org.mzi.test;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.mzi.api.ref.Ref;
import org.mzi.core.tele.Tele;
import org.mzi.core.term.Term;

import java.util.Map;
import java.util.Objects;

/**
 * @author ice1000
 */
@TestOnly
public interface Lisp {
  static @Nullable Term somehowParse(@NotNull String code) {
    return TermProducer.parse(code);
  }

  static @Nullable Term somehowParse(@NotNull String code, @NotNull Map<String, @NotNull Ref> refs) {
    return TermProducer.parse(code, refs);
  }

  static @NotNull Term reallyParse(@NotNull String code) {
    return Objects.requireNonNull(somehowParse(code));
  }

  static @NotNull Term reallyParse(@NotNull String code, @NotNull Map<String, @NotNull Ref> refs) {
    return Objects.requireNonNull(somehowParse(code, refs));
  }

  static @Nullable Tele somehowParseTele(@NotNull String code) {
    return TermProducer.parseTele(code);
  }

  static @NotNull Tele reallyParseTele(@NotNull String code) {
    return Objects.requireNonNull(somehowParseTele(code));
  }
}
