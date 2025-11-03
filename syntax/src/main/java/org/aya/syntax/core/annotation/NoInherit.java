// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.annotation;

import org.aya.syntax.core.term.Term;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/// Any method with `NoInherit` will disables dblity inspection, this is useful when the dblity does NOT inherit from the receiver.
///
/// @see org.aya.syntax.core.term.Term#instantiate(Term)
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE_USE)
public @interface NoInherit {
}
