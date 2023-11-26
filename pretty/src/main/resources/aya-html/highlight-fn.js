/*
 * Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
 * Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
 */

// When we hover over an Agda identifier, we highlight all occurrences of this identifier on the page.
// To this end, we create a map from identifier to all of its occurrences in the beginning.

// A dictionary from hrefs to 'a'-elements that have this href.
const dict = new Map();

function highlightFn(root) {
  // Get all 'a' tags with an 'href' attribute.
  // We call those "objects".
  const objs = root.querySelectorAll('a[href]');

  // Build a dictionary mapping a href to a set of objects that have this href.
  for (const obj of objs) {
    const key = obj.href;
    const set = dict.get(key) ?? new Set();
    set.add(obj);
    dict.set(key, set);
  }

  // Install 'onmouseover' and 'onmouseout' event handlers for all objects.
  for (const obj of objs) {
    // 'onmouseover' for an object adds attribute 'hover-highlight' to all objects with the same href.
    obj.onmouseover = function () {
      for (const o of dict.get(this.href)) {
        o.classList.add('hover-highlight');
      }
    }
    // 'onmouseover' removes the added 'hover-highlight' attributes again.
    obj.onmouseout = function () {
      for (const o of dict.get(this.href)) {
        o.classList.remove('hover-highlight');
      }
    }
  }
}

