// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler;

import kala.collection.immutable.ImmutableSeq;
import kala.range.primitive.IntRange;
import org.aya.syntax.compile.CompiledAya;
import org.aya.syntax.compile.JitCon;
import org.aya.syntax.core.def.TyckDef;
import org.aya.syntax.core.repr.AyaShape;
import org.aya.syntax.core.repr.CodeShape;
import org.aya.syntax.core.term.Param;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.telescope.JitTele;
import org.aya.util.binop.Assoc;
import org.jetbrains.annotations.NotNull;

import static org.aya.compiler.AyaSerializer.*;
import static org.aya.compiler.NameSerializer.javifyClassName;

public abstract class JitTeleSerializer<T extends TyckDef> extends AbstractSerializer<T> {
  public static final String CLASS_METADATA = ExprializeUtils.getJavaReference(CompiledAya.class);
  public static final String CLASS_JITCON = ExprializeUtils.getJavaReference(JitCon.class);
  public static final String CLASS_ASSOC = ExprializeUtils.getJavaReference(Assoc.class);
  public static final String CLASS_AYASHAPE = ExprializeUtils.getJavaReference(AyaShape.class);
  public static final String CLASS_GLOBALID = ExprializeUtils.makeSub(ExprializeUtils.getJavaReference(CodeShape.class), ExprializeUtils.getJavaReference(CodeShape.GlobalId.class));
  public static final String METHOD_TELESCOPE = "telescope";
  public static final String METHOD_RESULT = "result";
  public static final String TYPE_TERMSEQ = STR."\{CLASS_SEQ}<\{CLASS_TERM}>";

  protected final @NotNull Class<?> superClass;

  protected JitTeleSerializer(
    @NotNull SourceBuilder builder,
    @NotNull Class<?> superClass
  ) {
    super(builder);
    this.superClass = superClass;
  }

  protected void buildFramework(@NotNull T unit, @NotNull Runnable continuation) {
    var className = getClassName(unit);
    buildMetadata(unit);
    buildInnerClass(className, superClass, () -> {
      buildInstance(className);
      appendLine();     // make code more pretty
      // empty return type for constructor
      buildMethod(className, ImmutableSeq.empty(), "/*constructor*/", false, () -> buildConstructor(unit));
      appendLine();
      var iTerm = "i";
      var teleArgsTerm = "teleArgs";
      var teleArgsTy = TYPE_TERMSEQ;
      buildMethod(METHOD_TELESCOPE, ImmutableSeq.of(
        new JitParam(iTerm, "int"),
        new JitParam(teleArgsTerm, teleArgsTy)
      ), CLASS_TERM, true, () -> buildTelescope(unit, iTerm, teleArgsTerm));
      appendLine();
      buildMethod(METHOD_RESULT, ImmutableSeq.of(
        new JitParam(teleArgsTerm, teleArgsTy)
      ), CLASS_TERM, true, () -> buildResult(unit, teleArgsTerm));
      appendLine();
      continuation.run();
    });
  }

  private @NotNull String getClassName(@NotNull T unit) {
    return javifyClassName(unit.ref());
  }

  public void buildInstance(@NotNull String className) {
    appendLine(STR."public static final \{className} \{STATIC_FIELD_INSTANCE} = new \{className}();");
  }

  protected void appendMetadataRecord(@NotNull String name, @NotNull String value, boolean isFirst) {
    var prepend = isFirst ? "" : ", ";
    appendLine(STR."\{prepend}\{name} = \{value}");
  }

  /**
   * @see CompiledAya
   */
  protected void buildMetadata(@NotNull T unit) {
    var ref = unit.ref();
    var module = ref.module;
    var assoc = ref.assoc();
    var assocIdx = assoc == null ? -1 : assoc.ordinal();
    assert module != null;
    appendLine(STR."@\{CLASS_METADATA}(");
    var modPath = module.module().module();
    appendMetadataRecord("module", ExprializeUtils.makeHalfArrayFrom(modPath.view().map(ExprializeUtils::makeString)), true);
    // Assumption: module.take(fileModule.size).equals(fileModule)
    appendMetadataRecord("fileModuleSize", Integer.toString(module.fileModuleSize()), false);
    appendMetadataRecord("name", ExprializeUtils.makeString(ref.name()), false);
    appendMetadataRecord("assoc", Integer.toString(assocIdx), false);
    buildShape(unit);

    appendLine(")");
  }

  protected void buildShape(T unit) {
    appendMetadataRecord("shape", "-1", false);
    appendMetadataRecord("recognition", ExprializeUtils.makeHalfArrayFrom(ImmutableSeq.empty()), false);
  }

  /**
   * @see org.aya.syntax.compile.JitDef
   */
  protected abstract void buildConstructor(T unit);

  protected void buildConstructor(@NotNull T def, @NotNull ImmutableSeq<String> ext) {
    var tele = def.telescope();
    var size = tele.size();
    var licit = tele.view().map(Param::explicit).map(Object::toString);
    var names = tele.view().map(Param::name).map(x -> STR."\"\{x}\"");

    buildSuperCall(ImmutableSeq.of(
      Integer.toString(size),
      ExprializeUtils.makeArrayFrom("boolean", licit.toImmutableSeq()),
      ExprializeUtils.makeArrayFrom("java.lang.String", names.toImmutableArray())
    ).appendedAll(ext));
  }

  /**
   * @see JitTele#telescope(int, Term...)
   */
  protected void buildTelescope(@NotNull T unit, @NotNull String iTerm, @NotNull String teleArgsTerm) {
    var tele = unit.telescope();
    buildSwitch(iTerm, IntRange.closedOpen(0, tele.size()).collect(ImmutableSeq.factory()), kase ->
      buildReturn(serializeTermUnderTele(tele.get(kase).type(), teleArgsTerm, kase)), () -> buildPanic(null));
  }

  /**
   * @see JitTele#result
   */
  protected void buildResult(@NotNull T unit, @NotNull String teleArgsTerm) {
    buildReturn(serializeTermUnderTele(unit.result(), teleArgsTerm, unit.telescope().size()));
  }

  public void buildSuperCall(@NotNull ImmutableSeq<String> args) {
    appendLine(STR."super(\{args.joinToString(", ")});");
  }
}
