// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

public sealed interface FnCallLike extends Callable.Tele permits FnCall, RuleReducer.Fn  {
}
