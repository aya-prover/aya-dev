/*
 * Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
 * Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
 */

let currentHover = null;

function dismissTooltip() {
  if (currentHover) {
    currentHover.remove();
    currentHover = null;
  }
}

/** auto-dismissal of a tooltip */
function dismissTooltipIfNotUsed() {
  // Lock the current showing tooltip, in case a new hover is created.
  let lockedHover = currentHover;
  // When mouse leaves the error part of the code, we check the following thing after 1 second:
  // (1) If the mouse is inside the tooltip popup, we do nothing.
  // (2) If the tooltip popup is clicked, we do nothing.
  // Otherwise, close the tooltip popup.
  if (lockedHover) setTimeout(() => {
    // If the current hover is still the same as the locked hover...
    if (lockedHover === currentHover && !lockedHover.userIsThinking && !lockedHover.userClicked)
      dismissTooltip();
  }, 1000);
}

// https://github.com/plt-amy/1lab/blob/5e5a22abce8a5cfb62b5f815e1231c1e34bb0a12/support/web/js/highlight-hover.ts#L22
function showTooltip(on) {
  return function () {
    let link = this;
    const text = link.getAttribute("data-tooltip-text");
    if (!text) return;
    if (on) {
      // If the tooltip for the error code is already shown, and user once clicked it,
      // do not recreate it again, because `userClicked` may be lost, allowing the tooltip to be
      // dismissed after a certain time.
      if (currentHover && currentHover.userCreatedFrom === link && currentHover.userClicked) {
        return;
      }
      // dismiss the previous tooltip because a new one is being created and shown
      dismissTooltip();

      // create the tooltip with inner HTML decoded from base64 data
      currentHover = document.createElement("div");
      currentHover.innerHTML = atob(text);
      currentHover.classList.add("AyaTooltipPopup");
      currentHover.userCreatedFrom = link;
      // Hover to highlight occurrences is done by adding mouse event listeners to the elements in the tooltip.
      // The inserted tooltip is not a child of `document` when the page was loaded, so a manual setup is needed.
      setupHighlight(currentHover);
      currentHover.addEventListener("click", ev => {
        // Clicking on a tooltip disables the auto-dismissal.
        currentHover.userClicked = true;
      });
      currentHover.addEventListener("mouseover", ev => {
        currentHover.userIsThinking = true
      });
      currentHover.addEventListener("mouseout", ev => {
        currentHover.userIsThinking = false;
        dismissTooltipIfNotUsed();
      });
      currentHover.tabIndex = 0;
      currentHover.addEventListener("keydown", ev => {
        if (ev.key === "Escape") dismissTooltip();
      });

      // THE BIG GAME!
      document.body.appendChild(currentHover);

      const selfRect = link.getBoundingClientRect();
      const hoverRect = currentHover.getBoundingClientRect();
      // If we're close to the bottom of the page, push the tooltip above instead.
      // The constant here is arbitrary, because trying to convert em to px in JS is a fool's errand.
      if (selfRect.bottom + hoverRect.height + 30 > window.innerHeight) {
        // 3em for showing above the type hover
        currentHover.style.top = `calc(${link.offsetTop - hoverRect.height + 8}px - 3em)`;
      } else {
        currentHover.style.top = `${link.offsetTop + link.offsetHeight + 8}px`;
      }
      currentHover.style.left = `${link.offsetLeft}px`;
    } else {
      // When mouse leaves error code part, fire an auto-dismissal.
      dismissTooltipIfNotUsed();
    }
  }
}
