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

public sealed interface AstExpr extends Docile {
  record New(@NotNull MethodRef conRef, @NotNull ImmutableSeq<AstVariable> args) implements AstExpr {
    @Override public @NotNull Doc toDoc() {
      return Doc.sep(
        Doc.styled(BasePrettier.KEYWORD, "new"),
        Doc.wrap("(", ")", Doc.commaList(args.view().map(AstVariable::toDoc)))
      );
    }
  }

  record Invoke(@NotNull MethodRef methodRef, @Nullable AstVariable owner,
                @NotNull ImmutableSeq<AstVariable> args) implements AstExpr {
    @Override public @NotNull Doc toDoc() {
      Doc ownerDoc = owner != null
        ? owner.toDoc()
        : Doc.plain(methodRef.owner().displayName());
      return Doc.sep(
        Doc.styled(BasePrettier.KEYWORD, "invoke"),
        Doc.cat(ownerDoc, Doc.plain("."), Doc.plain(methodRef.name())),
        Doc.wrap("(", ")", Doc.commaList(args.view().map(AstVariable::toDoc)))
      );
    }
  }

  record Ref(@NotNull AstVariable variable) implements AstExpr {
    @Override public @NotNull Doc toDoc() { return variable.toDoc(); }
  }

  record RefField(@NotNull FieldRef fieldRef, @Nullable AstVariable owner) implements AstExpr {
    @Override public @NotNull Doc toDoc() {
      if (owner != null) {
        return Doc.cat(owner.toDoc(), Doc.plain("."), Doc.plain(fieldRef.name()));
      } else {
        return Doc.cat(Doc.plain(fieldRef.owner().displayName() + "."), Doc.plain(fieldRef.name()));
      }
    }
  }

  record RefEnum(@NotNull ClassDesc enumClass, @NotNull String enumName) implements AstExpr {
    @Override public @NotNull Doc toDoc() {
      return Doc.plain(enumClass.displayName() + "." + enumName);
    }
  }

  record Lambda(@NotNull ImmutableSeq<AstVariable> captures, @NotNull MethodRef method,
                @NotNull ImmutableSeq<AstStmt> body) implements AstExpr {
    @Override public @NotNull Doc toDoc() {
      return Doc.vcat(Doc.sep(
          Doc.styled(BasePrettier.KEYWORD, "lambda capturing "),
          Doc.commaList(captures.view().map(AstVariable::toDoc))),
        Doc.nest(2, Doc.vcat(body.view().map(AstStmt::toDoc))));
    }
  }

  sealed interface Const extends AstExpr, AstValue { }
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
  enum This implements Const {
    INSTANCE;
    @Override public @NotNull Doc toDoc() {
      return Doc.styled(BasePrettier.KEYWORD, "this");
    }
  }

  record Array(
    @NotNull ClassDesc type, int length,
    @Nullable ImmutableSeq<AstVariable> initializer
  ) implements AstExpr {
    @Override public @NotNull Doc toDoc() {
      var header = Doc.sep(
        Doc.styled(BasePrettier.KEYWORD, "array"),
        Doc.plain(type.displayName()),
        Doc.wrap("[", "]", Doc.plain(String.valueOf(length))));
      if (initializer == null) return header;
      if (initializer.sizeLessThanOrEquals(10))
        return Doc.sep(
          header,
          Doc.commaList(initializer.view().map(AstVariable::toDoc)));
      else return Doc.vcat(
        header,
        Doc.styled(BasePrettier.KEYWORD, "elements"),
        Doc.nest(2, Doc.commaList(initializer.view().map(AstVariable::toDoc)))
      );
    }
  }

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
