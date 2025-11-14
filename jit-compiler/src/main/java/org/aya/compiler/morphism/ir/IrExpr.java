// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.morphism.ir;

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

public sealed interface IrExpr extends Docile {
  record New(@NotNull MethodRef conRef, @NotNull ImmutableSeq<IrValue> args) implements IrExpr {
    @Override public @NotNull Doc toDoc() {
      return Doc.sep(
        Doc.styled(BasePrettier.KEYWORD, "new"),
        Doc.wrap("(", ")", Doc.commaList(args.view().map(IrValue::toDoc)))
      );
    }
  }

  record Invoke(@NotNull MethodRef methodRef, @Nullable IrValue owner,
                @NotNull ImmutableSeq<IrValue> args) implements IrExpr {
    @Override public @NotNull Doc toDoc() {
      Doc ownerDoc = owner != null
        ? owner.toDoc()
        : Doc.plain(methodRef.owner().displayName());
      return Doc.sep(
        Doc.styled(BasePrettier.KEYWORD, "invoke"),
        Doc.cat(ownerDoc, Doc.plain("."), Doc.plain(methodRef.name())),
        Doc.wrap("(", ")", Doc.commaList(args.view().map(IrValue::toDoc)))
      );
    }
  }

  record Ref(@NotNull IrVariable variable) implements IrExpr {
    @Override public @NotNull Doc toDoc() { return variable.toDoc(); }
  }

  record RefField(@NotNull FieldRef fieldRef, @Nullable IrValue owner) implements IrExpr {
    @Override public @NotNull Doc toDoc() {
      if (owner != null) {
        return Doc.cat(owner.toDoc(), Doc.plain("."), Doc.plain(fieldRef.name()));
      } else {
        return Doc.cat(Doc.plain(fieldRef.owner().displayName() + "."), Doc.plain(fieldRef.name()));
      }
    }
  }

  record RefEnum(@NotNull ClassDesc enumClass, @NotNull String enumName) implements IrExpr {
    @Override public @NotNull Doc toDoc() {
      return Doc.plain(enumClass.displayName() + "." + enumName);
    }
  }

  record Lambda(@NotNull ImmutableSeq<IrVariable> captures, @NotNull MethodRef method,
                @NotNull ImmutableSeq<IrStmt> body) implements IrExpr {
    @Override public @NotNull Doc toDoc() {
      return Doc.vcat(Doc.sep(
          Doc.styled(BasePrettier.KEYWORD, "lambda capturing "),
          Doc.commaList(captures.view().map(IrVariable::toDoc))),
        Doc.nest(2, Doc.vcat(body.view().map(IrStmt::toDoc))));
    }
  }

  sealed interface Const extends IrExpr, IrValue { }
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
    @Nullable ImmutableSeq<IrValue> initializer
  ) implements IrExpr {
    @Override public @NotNull Doc toDoc() {
      var header = Doc.sep(
        Doc.styled(BasePrettier.KEYWORD, "array"),
        Doc.plain(type.displayName()),
        Doc.wrap("[", "]", Doc.plain(String.valueOf(length))));
      if (initializer == null) return header;
      if (initializer.sizeLessThanOrEquals(10))
        return Doc.sep(
          header,
          Doc.commaList(initializer.view().map(IrValue::toDoc)));
      else return Doc.vcat(
        header,
        Doc.styled(BasePrettier.KEYWORD, "elements"),
        Doc.nest(2, Doc.commaList(initializer.view().map(IrValue::toDoc)))
      );
    }
  }

  record GetArray(@NotNull IrVariable array, int index) implements IrExpr {
    @Override public @NotNull Doc toDoc() {
      return Doc.cat(array.toDoc(), Doc.wrap("[", "]", Doc.plain(String.valueOf(index))));
    }
  }

  record CheckCast(@NotNull IrValue obj, @NotNull ClassDesc as) implements IrExpr {
    @Override public @NotNull Doc toDoc() {
      return Doc.sep(
        Doc.styled(BasePrettier.KEYWORD, "checkcast"),
        obj.toDoc(),
        Doc.plain(as.displayName())
      );
    }
  }
}
