// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler;

import kala.collection.immutable.ImmutableSeq;
import kala.range.primitive.IntRange;
import org.aya.generic.NameGenerator;
import org.aya.syntax.compile.CompiledAya;
import org.aya.syntax.compile.JitCon;
import org.aya.syntax.core.def.TyckDef;
import org.aya.syntax.core.repr.AyaShape;
import org.aya.syntax.core.repr.CodeShape;
import org.aya.syntax.core.term.Param;
import org.aya.syntax.core.term.Term;
import org.aya.util.binop.Assoc;
import org.jetbrains.annotations.NotNull;

public abstract class JitTeleSerializer<T extends TyckDef> extends AbstractSerializer<T> {
  public static final String CLASS_METADATA = getJavaReference(CompiledAya.class);
  public static final String CLASS_JITCON = getJavaReference(JitCon.class);
  public static final String CLASS_ASSOC = getJavaReference(Assoc.class);
  public static final String CLASS_AYASHAPE = getJavaReference(AyaShape.class);
  public static final String CLASS_GLOBALID = makeSub(getJavaReference(CodeShape.class), getJavaReference(CodeShape.GlobalId.class));
  public static final String METHOD_TELESCOPE = "telescope";
  public static final String METHOD_RESULT = "result";
  public static final String TYPE_TERMSEQ = STR."\{CLASS_SEQ}<\{CLASS_TERM}>";

  protected final @NotNull Class<?> superClass;

  protected JitTeleSerializer(
    @NotNull StringBuilder builder,
    int indent,
    @NotNull NameGenerator nameGen,
    @NotNull Class<?> superClass
  ) {
    super(builder, indent, nameGen);
    this.superClass = superClass;
  }

  protected JitTeleSerializer(
    @NotNull AbstractSerializer<?> serializer,
    @NotNull Class<?> superClass
  ) {
    super(serializer);
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
    return javify(unit.ref());
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
    appendMetadataRecord("module", makeHalfArrayFrom(modPath.view().map(JitTeleSerializer::makeString)), true);
    // Assumption: module.take(fileModule.size).equals(fileModule)
    appendMetadataRecord("fileModuleSize", Integer.toString(module.fileModuleSize()), false);
    appendMetadataRecord("name", makeString(ref.name()), false);
    appendMetadataRecord("assoc", Integer.toString(assocIdx), false);
    buildShape(unit);

    appendLine(")");
  }

  protected void buildShape(T unit) {
    appendMetadataRecord("shape", "-1", false);
    appendMetadataRecord("recognition", makeHalfArrayFrom(ImmutableSeq.empty()), false);
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
      makeArrayFrom("boolean", licit.toImmutableSeq()),
      makeArrayFrom("String", names.toImmutableArray())
    ).appendedAll(ext));
  }

  /**
   * @see org.aya.syntax.compile.JitTele#telescope(int, Term...)
   */
  protected void buildTelescope(@NotNull T unit, @NotNull String iTerm, @NotNull String teleArgsTerm) {
    @NotNull ImmutableSeq<Param> tele = unit.telescope();
    buildSwitch(iTerm, IntRange.closedOpen(0, tele.size()).collect(ImmutableSeq.factory()), kase ->
      buildReturn(serializeTermUnderTele(tele.get(kase).type(), teleArgsTerm, kase)), () -> buildPanic(null));
  }

  /**
   * @see org.aya.syntax.compile.JitTele#result
   */
  protected void buildResult(@NotNull T unit, @NotNull String teleArgsTerm) {
    buildReturn(serializeTermUnderTele(unit.result(), teleArgsTerm, unit.telescope().size()));
  }

  public void buildSuperCall(@NotNull ImmutableSeq<String> args) {
    appendLine(STR."super(\{args.joinToString(", ")});");
  }
}
