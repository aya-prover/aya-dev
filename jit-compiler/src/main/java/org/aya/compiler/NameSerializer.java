// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler;

import kala.collection.SeqView;
import org.aya.syntax.core.def.AnyDef;
import org.aya.syntax.core.def.TyckAnyDef;
import org.aya.syntax.ref.DefVar;
import org.aya.syntax.ref.ModulePath;
import org.aya.syntax.ref.QName;
import org.aya.syntax.ref.QPath;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Objects;
import java.util.stream.IntStream;

import static org.aya.compiler.AyaSerializer.PACKAGE_BASE;

public interface NameSerializer {
  String PATH_SEPARATOR = File.separator;
  String PACKAGE_SEPARATOR = ".";
  String NEST_CLASS_SEPARATOR = ".";
  String CLASS_NAME_SEPARATOR = "$";

  enum NameType {
    // class reference in java source code, i.e. "foo.bar.nestClass"
    ClassReference(PACKAGE_SEPARATOR, NEST_CLASS_SEPARATOR),
    // class name that used for loading class, i.e. "foo.bar$nestClass"
    ClassName(PACKAGE_SEPARATOR, CLASS_NAME_SEPARATOR),
    // class path that used for finding class file, i.e. "foo/bar$nestClass"
    ClassPath(PATH_SEPARATOR, CLASS_NAME_SEPARATOR);

    public final @NotNull String packageSeparator;
    public final @NotNull String classNameSeparactor;

    NameType(@NotNull String packageSeparator, @NotNull String classNameSeparactor) {
      this.packageSeparator = packageSeparator;
      this.classNameSeparactor = classNameSeparactor;
    }
  }

  /**
   * Compute the package reference of certain <b>file level</b> {@link ModulePath}.
   */
  static @NotNull String getModulePackageReference(@NotNull ModulePath module, @NotNull String separator) {
    return module.module().view().dropLast(1)
      .prepended(PACKAGE_BASE)
      .joinToString(separator, NameSerializer::javify);
  }

  /**
   * Compute the qualified name for certain {@link QPath module}/symbol in {@link QPath module}.
   * You may want to specify {@param separator} for different use.
   */
  static @NotNull String getReference(@NotNull QPath module, @Nullable String name, @NotNull NameType type) {
    // get package name of file level module
    var packageName = getModulePackageReference(module.fileModule(), type.packageSeparator);
    // get javify class name of each component
    var javifyComponent = module.traversal((path) -> javifyClassName(path, null)).view();
    if (name != null) javifyComponent = javifyComponent.appended(javifyClassName(module, name));
    return STR."\{packageName}\{type.packageSeparator}\{javifyComponent.joinToString(type.classNameSeparactor)}";
  }

  static @NotNull String getClassReference(@NotNull QPath module, @Nullable String name) {
    return getReference(module, name, NameType.ClassReference);
  }

  static @NotNull String getClassName(@NotNull QName name) {
    return getClassName(name.module(), name.name());
  }

  static @NotNull String getClassName(@NotNull QPath module, @Nullable String name) {
    return getReference(module, name, NameType.ClassName);
  }

  static @NotNull String getModuleReference(@NotNull QPath module) {
    return getClassReference(module, null);
  }

  static @NotNull String getClassReference(@NotNull QName name) {
    return getClassReference(name.module(), name.name());
  }

  static @NotNull String getClassReference(@NotNull DefVar<?, ?> ref) {
    return getClassReference(TyckAnyDef.make(ref.core));
  }

  /**
   * Obtain the java qualified name of certain {@link AnyDef def}
   *
   * @see #getReference(QPath, String, NameType)
   */
  static @NotNull String getClassReference(@NotNull AnyDef def) {
    return getClassReference(def.qualifiedName());
  }

  static @NotNull String javifyClassName(@NotNull QPath path, @Nullable String name) {
    var ids = path.module().module()
      .view().drop(path.fileModuleSize() - 1);
    if (name != null) ids = ids.appended(name);
    return javifyClassName(ids);
  }

  /** Mangle an aya symbol name to a java symbol name */
  static @NotNull String javifyClassName(@NotNull DefVar<?, ?> ayaName) {
    return javifyClassName(Objects.requireNonNull(ayaName.module), ayaName.name());
  }

  /**
   * Generate a java friendly class name of {@param ids}, this function should be one-to-one
   *
   * @param ids the qualified id that may refer to a {@link org.aya.syntax.concrete.stmt.ModuleName module}
   *            or {@link org.aya.syntax.concrete.stmt.QualifiedID definition},
   *            note that {@link org.aya.syntax.concrete.stmt.ModuleName.ThisRef} should
   *            be replaced with the name of the file level module.
   */
  static @NotNull String javifyClassName(@NotNull SeqView<String> ids) {
    return ids.map(NameSerializer::javify)
      .joinToString("$", "$", "");
  }

  /**
   * Generate a java friendly name for {@param name}, this function should be one-to-one.
   * Note that the result may not be used for class name, see {@link #javifyClassName}
   */
  static @NotNull String javify(String name) {
    return name.codePoints().flatMap(x ->
        x == '$' ? "$$".chars()
          : Character.isJavaIdentifierPart(x) ? IntStream.of(x)
            : ("$" + x).chars())
      .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
      .toString();
  }
}
