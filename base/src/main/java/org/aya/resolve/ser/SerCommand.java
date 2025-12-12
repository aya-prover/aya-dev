// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve.ser;

import java.io.Serializable;

public sealed interface SerCommand extends Serializable permits SerImport, SerModule, SerOpen { }
