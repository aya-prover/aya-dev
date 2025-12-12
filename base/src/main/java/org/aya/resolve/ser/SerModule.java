// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve.ser;

import kala.collection.immutable.ImmutableSeq;
import org.jetbrains.annotations.NotNull;

public record SerModule(@NotNull String name, @NotNull ImmutableSeq<SerCommand> commands) implements SerCommand { }
