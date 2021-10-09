// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.pretty.backend.string;

import org.jetbrains.annotations.NotNull;

import java.io.Serializable;

/**
 * @author imkiva
 */
public record LinkId(@NotNull String id) implements Serializable {
}
