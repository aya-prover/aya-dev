// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.morphism.ast;

import com.intellij.openapi.util.text.StringUtil;
import kala.collection.immutable.ImmutableSeq;
import org.aya.compiler.FieldRef;
import org.aya.compiler.MethodRef;
import org.aya.prettier.BasePrettier;
import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Docile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.constant.ClassDesc;

// TODO: consider [AstExpr#type]
public sealed interface AstExpr extends Docile {
  default @Override @NotNull Doc toDoc() {
    return Doc.plain("<unimplemented pretty print>");
  }
  record New(@NotNull MethodRef conRef, @NotNull ImmutableSeq<AstVariable> args) implements AstExpr { }
  record Invoke(@NotNull MethodRef methodRef, @Nullable AstVariable owner,
                @NotNull ImmutableSeq<AstVariable> args) implements AstExpr { }
  record Ref(@NotNull AstVariable variable) implements AstExpr { }
  record RefField(@NotNull FieldRef fieldRef, @Nullable AstVariable owner) implements AstExpr { }
  record RefEnum(@NotNull ClassDesc enumClass, @NotNull String enumName) implements AstExpr { }
  record Lambda(@NotNull ImmutableSeq<AstVariable> captures, @NotNull MethodRef method,
                @NotNull ImmutableSeq<AstStmt> body) implements AstExpr { }

  sealed interface Const extends AstExpr { }
  record Iconst(int value) implements Const {
    @Override public @NotNull Doc toDoc() {
      return Doc.plain(Integer.toString(value));
    }
  }
  record Bconst(boolean value) implements Const {
    @Override public @NotNull Doc toDoc() {
      return Doc.styled(BasePrettier.KEYWORD, Boolean.toString(value));
    }
  }
  record Sconst(@NotNull String value) implements Const {
    @Override public @NotNull Doc toDoc() {
      return Doc.plain(StringUtil.escapeStringCharacters(value));
    }
  }
  record Null(@NotNull ClassDesc type) implements Const {
    @Override public @NotNull Doc toDoc() {
      return Doc.styled(BasePrettier.KEYWORD, "null");
    }
  }
  enum This implements AstExpr {
    INSTANCE;
    @Override public @NotNull Doc toDoc() {
      return Doc.styled(BasePrettier.KEYWORD, "this");
    }
  }

  record Array(@NotNull ClassDesc type, int length,
               @Nullable ImmutableSeq<AstVariable> initializer) implements AstExpr { }

  record GetArray(@NotNull AstVariable array, int index) implements AstExpr {
    @Override public @NotNull Doc toDoc() {
      return Doc.cat(array.toDoc(), Doc.wrap("[", "]", Doc.plain(String.valueOf(index))));
    }
  }

  record CheckCast(@NotNull AstVariable obj, @NotNull ClassDesc as) implements AstExpr {
    @Override public @NotNull Doc toDoc() {
      return Doc.sep(
        Doc.styled(BasePrettier.KEYWORD, "checkcast"),
        obj.toDoc(),
        Doc.plain(as.displayName())
      );
    }
  }
}
