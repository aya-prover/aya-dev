// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.term.call;

/**
 * ClassCall is a very special construction in Aya.
 * <ul>
 *   <li>It is like a type when partially instantiated -- the type of "specifications" of the rest of the fields.</li>
 *   <li>It is like a term when fully instantiated, whose type can be anything.</li>
 *   <li>It can be applied like a function, which essentially inserts the nearest missing field.</li>
 * </ul>
 *
 * @author kiva, ice1000
 */
// public record ClassCall(
//   @NotNull LocalVar self,
//   @Override @NotNull DefVar<ClassDef, ClassDecl> ref,
//   @Override int ulift,
//   @NotNull ImmutableMap<DefVar<MemberDef, TeleDecl.ClassMember>, Term> args
// ) implements StableWHNF, Formation {
// }
