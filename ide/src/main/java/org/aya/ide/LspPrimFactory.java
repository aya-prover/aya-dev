// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.ide;

import org.aya.states.primitive.PrimFactory;
import org.aya.syntax.concrete.stmt.decl.PrimDecl;
import org.aya.syntax.core.def.PrimDef;
import org.aya.syntax.core.def.PrimDefLike;
import org.aya.syntax.ref.DefVar;
import org.jetbrains.annotations.NotNull;

/// TL; DR: Patched primitive factory that reuses primitives from last compilation.
///
/// Our LSP reuses unchanged sources and only compiles changed ones. So there exist
/// a scenario where the file containing primitive declarations (for example, Primitives.aya)
/// can be unchanged while its importers are modified by users --- in this case,
/// the recompile list does not contain Primitives.aya so primitives previously created and stored in
/// the factory should be reused, thus we cannot clear the primitive factory after every
/// recompilation.
///
/// Unfortunately, the original primitive factory does not fit our needs in the situation described above,
/// which always reports redefinition errors because it is not designed to be used in an incremental compiler.
///
/// Patches in this file is safe (no definition leak). This can be proven by case-split:
///
/// When Primitives.aya is unchanged, the [org.aya.cli.library.incremental.InMemoryCompilerAdvisor]
/// keeps track of the last compilation result (namely [org.aya.resolve.ResolveInfo]), which ensures the
/// re-resolve of changed source files always points primitives to the previously created [PrimDef]s.
///
/// When Primitives.aya is changed, the [org.aya.cli.library.LibraryCompiler] will recompile it together with
/// its importers, which is a full remake of all primitive-related source files and the
/// [org.aya.cli.library.incremental.InMemoryCompilerAdvisor] will be updated to the newest compilation result.
public class LspPrimFactory extends PrimFactory {
  /** Allow all kinds of redefinition. */
  @Override public boolean isForbiddenRedefinition(PrimDef.@NotNull ID id, boolean isJit) {
    return false;
  }
  @Override public @NotNull PrimDefLike factory(PrimDef.@NotNull ID name, @NotNull DefVar<PrimDef, PrimDecl> ref) {
    return getOption(name).getOrElse(() -> super.factory(name, ref));
  }
}
