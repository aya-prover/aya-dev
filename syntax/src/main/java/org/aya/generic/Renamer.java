// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.generic;

import kala.collection.mutable.MutableMap;
import org.aya.syntax.core.pat.PatToTerm;
import org.aya.syntax.core.term.*;
import org.aya.syntax.core.term.call.Callable;
import org.aya.syntax.core.term.xtt.CoeTerm;
import org.aya.syntax.core.term.xtt.DimTyTerm;
import org.aya.syntax.core.term.xtt.EqTerm;
import org.aya.syntax.core.term.xtt.PAppTerm;
import org.aya.syntax.ref.LocalCtx;
import org.aya.syntax.ref.LocalVar;
import org.jetbrains.annotations.NotNull;

public record Renamer(MutableMap<String, LocalVar> scope) {
  public Renamer() {
    this(MutableMap.create());
  }

  public void store(@NotNull LocalCtx ctx) {
    ctx.extract().forEach(lv -> scope.put(lv.name(), lv));
  }

  public static @NotNull String nameOf(@NotNull Term ty) {
    return switch (ty) {
      case FreeTerm(var name) -> name.name();
      case MetaPatTerm(var meta) -> {
        var solution = meta.solution().get();
        if (solution == null) yield "p";
        yield nameOf(PatToTerm.visit(solution));
      }
      case Callable.Tele c -> Character.toString(Character.toLowerCase(
        c.ref().name().codePointAt(0)));
      case PiTerm _ -> "f";
      case SigmaTerm _ -> "t";
      case DimTyTerm _ -> "i";
      case ProjTerm p -> nameOf(p.of());
      case AppTerm a -> nameOf(a.fun());
      case PAppTerm a -> nameOf(a.fun());
      case EqTerm _, CoeTerm _ -> "p";
      default -> "x";
    };
  }

  public @NotNull LocalVar bindName(@NotNull Term name) {
    return bindName(nameOf(name));
  }
  public @NotNull LocalVar bindName(@NotNull String name) {
    if (scope.containsKey(name)) {
      var newGame = sanitizeName(name);
      var uid = LocalVar.generate(newGame);
      scope.put(newGame, uid);
      return uid;
    } else {
      var uid = LocalVar.generate(name);
      scope.put(name, uid);
      return uid;
    }
  }

  private String sanitizeName(String name) {
    if (name.length() == 1) {
      var c = name.codePointAt(0);
      if (c >= 'a' && c <= 'z') {
        var ideal = conflictAlphabetic(c, 'a');
        if (ideal != null) return ideal;
      }
      if (c >= 'A' && c <= 'Z') {
        var ideal = conflictAlphabetic(c, 'A');
        if (ideal != null) return ideal;
      }
    }
    return conflictSuffix(name);
  }

  private String conflictAlphabetic(int c, int base) {
    int initial = c - base;
    int i = initial + 1;
    while (scope.containsKey(Character.toString(i + base))) {
      if (i == initial) return null;
      i = (i + 1) % 26;
    }
    return Character.toString(i + base);
  }

  private String conflictSuffix(String name) {
    int i = 0;
    while (scope.containsKey(name + i)) i++;
    return name + i;
  }

  public void unbindName(@NotNull LocalVar uid) {
    scope.remove(uid.name());
  }
}
