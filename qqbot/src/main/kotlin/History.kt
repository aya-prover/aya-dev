// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.qqbot

import kala.collection.mutable.Buffer
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*

object History {
  init {
    Paths.get("record").let {
      if (!Files.exists(it))
        Files.createFile(it)
    }
  }

  private val history = Buffer.from(Files.readAllLines(Paths.get("record")))

  fun get(x: Int): Path = Paths.get("tmp", history[x])

  fun add(s: ByteArray): Path {
    val id = UUID.randomUUID().toString()
    val file = Paths.get("tmp", id)
    Files.write(file, s)
    history.prepend(id)
    save()
    return file
  }

  private fun save() {
    Files.write(Paths.get("record"), history.joinToString("\n").toByteArray())
  }
}
