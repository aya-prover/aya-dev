// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.ide;

import org.aya.primitive.PrimFactory;
import org.aya.syntax.concrete.stmt.decl.PrimDecl;
import org.aya.syntax.core.def.PrimDef;
import org.aya.syntax.ref.DefVar;
import org.jetbrains.annotations.NotNull;

/**
 * TL; DR: Patched primitive factory that reuses primitives from last compilation.
 * <p>
 * Our LSP reuses unchanged sources and only compiles changed ones. So there exist
 * a scenario where the file containing primitive declarations (for example, Primitives.aya)
 * can be unchanged while its importers are modified by users --- in this case,
 * the recompile list does not contain Primitives.aya so primitives previously created and stored in
 * the factory should be reused, thus we cannot clear the primitive factory after every
 * recompilation.
 * <p>
 * Unfortunately, the original primitive factory does not fit our needs in the situation described above,
 * which always reports redefinition errors because it is not designed to be used in an incremental compiler.
 * <p>
 * <p>
 * Patches in this file is safe (no definition leak). This can be proven by case-split:
 * <p>
 * When Primitives.aya is unchanged, the {@link org.aya.cli.library.incremental.InMemoryCompilerAdvisor}
 * keeps track of the last compilation result (namely {@link org.aya.resolve.ResolveInfo}), which ensures the
 * re-resolve of changed source files always points primitives to the previously created {@link PrimDef}s.
 * <p>
 * When Primitives.aya is changed, the {@link org.aya.cli.library.LibraryCompiler} will recompile it together with
 * its importers, which is a full remake of all primitive-related source files and the
 * {@link org.aya.cli.library.incremental.InMemoryCompilerAdvisor} will be updated to the newest compilation result.
 */
public class LspPrimFactory extends PrimFactory {
  @Override public boolean suppressRedefinition() { return true; }

  @Override public @NotNull PrimDef factory(PrimDef.@NotNull ID name, @NotNull DefVar<PrimDef, PrimDecl> ref) {
    return getOption(name).getOrElse(() -> super.factory(name, ref));
  }
}
