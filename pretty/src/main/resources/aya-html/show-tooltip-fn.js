/*
 * Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
 * Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
 */

// https://stackoverflow.com/questions/30106476/using-javascripts-atob-to-decode-base64-doesnt-properly-decode-utf-8-strings
function b64DecodeUnicode(str) {
  // Going backwards: from bytestream, to percent-encoding, to original string.
  return decodeURIComponent(atob(str).split('').map(function (c) {
    return '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2);
  }).join(''));
}

class HoverStack {
  constructor() {
    this.list = [];
  }

  dismiss(hover) {
    if (hover) {
      hover.remove();
      this.list = this.list.filter(x => x !== hover);
    }
  }

  dismissIfNotUsed(hover) {
    // When mouse leaves the error part of the code, we check the following thing after 1 second:
    // (1) If the mouse is inside the tooltip popup, we do nothing.
    // (2) If the tooltip popup is clicked, we do nothing.
    // Otherwise, close the tooltip popup.
    if (hover) {
      hover.markedForDismissal = true;
      setTimeout(() => {
        // If the current hover is still the same as the locked hover...
        if (!hover.userIsThinking && this.allowAutoDismissal(hover))
          this.dismiss(hover)
      }, 1000);
    }
  }

  allowAutoDismissal(hover) {
    return hover.markedForDismissal && !hover.userClicked;
  }

  fireAutoDismissalFor(link) {
    let hover = this.list.find(hover => hover.userCreatedFrom === link);
    this.dismissIfNotUsed(hover);
  }

  createHoverFor(link, text) {
    // if the tooltip for the error code is already shown, and user once clicked it,
    // do not recreate it again, because `userClicked` may be lost, allowing the tooltip to be
    // dismissed after a certain time.
    let old = this.list.find(hover => hover.userCreatedFrom === link);
    if (old) {
      if (old.userClicked) return old;
    }
    // Find all links that overlap with the current link
    let dismissNow = [];
    const nested = this.list.filter(hover => {
      if (this.allowAutoDismissal(hover)) {
        dismissNow.push(hover);
        return false;
      }
      const hoverLink = hover.userCreatedFrom;
      const currentLink = link;
      // find if the two links have super common parent node
      let parent = currentLink;
      while (parent) {
        if (parent === hoverLink) return true;
        parent = parent.parentElement;
      }
      parent = hoverLink;
      while (parent) {
        if (parent === currentLink) return true;
        parent = parent.parentElement;
      }
      return false;
    });
    dismissNow.forEach(x => this.dismiss(x));

    // this is a new tooltip, create it
    let newHover = document.createElement("div");
    newHover.userCreatedFrom = link;
    // set the content from base64 encoded attribute data-tooltip-text
    newHover.innerHTML = "<span id='AyaTooltipPopupClose'>&times;</span>" + b64DecodeUnicode(text);
    newHover.classList.add("AyaTooltipPopup");
    // Hover to highlight occurrences is done by adding mouse event listeners to the elements in the tooltip.
    // The inserted tooltip is not a child of `document` when the page was loaded, so a manual setup is needed.
    setupHighlight(newHover);

    // Auto-dismissal setup
    newHover.addEventListener("click", ev => {
      // Clicking on a tooltip disables the auto-dismissal.
      if (newHover) {
        newHover.userClicked = true;
        newHover.markedForDismissal = false;
      }
      // Show the close button
      let close = document.getElementById('AyaTooltipPopupClose');
      if (!close) return; // already closed
      console.log("find close button: " + close.innerText);
      close.style.visibility = "visible";
      close.addEventListener("click", ev => this.dismiss(newHover));
    });
    newHover.addEventListener("mouseover", ev => {
      if (newHover) newHover.userIsThinking = true
    });
    newHover.addEventListener("mouseout", ev => {
      if (newHover) newHover.userIsThinking = false;
      this.dismissIfNotUsed(newHover);
    });
    newHover.tabIndex = 0;

    // calculate the position of the tooltip
    if (nested.length === 0) {
      const selfRect = link.getBoundingClientRect();
      const hoverRect = newHover.getBoundingClientRect();
      // If we're close to the bottom of the page, push the tooltip above instead.
      // The constant here is arbitrary, because trying to convert em to px in JS is a fool's errand.
      if (selfRect.bottom + hoverRect.height + 30 > window.innerHeight) {
        // 3em for showing above the type hover
        newHover.style.top = `calc(${link.offsetTop - hoverRect.height + 8}px - 3em)`;
      } else {
        newHover.style.top = `${link.offsetTop + link.offsetHeight + 8}px`;
      }
      newHover.style.left = `${link.offsetLeft}px`;
    } else {
      // If there are other tooltips, put this one below the last one.
      const belowest = Math.max(...nested.map(hover => hover.offsetTop + hover.offsetHeight));
      newHover.style.top = `${belowest + 8}px`;
      newHover.style.left = `${link.offsetLeft}px`;
    }

    // THE BIG GAME!
    this.list.push(newHover);
    return newHover;
  }
}

let hoverStack = new HoverStack();

// https://github.com/plt-amy/1lab/blob/5e5a22abce8a5cfb62b5f815e1231c1e34bb0a12/support/web/js/highlight-hover.ts#L22
function showTooltip(on) {
  return function () {
    let link = this;
    const text = link.getAttribute("data-tooltip-text");
    if (!text) return;
    // FIXME: if two problems are overlapping, only the top problem is shown, according to "event bubbling"
    //  the bottom problem is shown, but quickly get dismissed by the top problem.
    console.log("[" + on + "]" + " showing tooltip for " + link.innerText);
    if (on) {
      let currentHover = hoverStack.createHoverFor(link, text);
      // THE BIG GAME!
      document.body.appendChild(currentHover);
    } else {
      // When mouse leaves error code part, fire an auto-dismissal.
      hoverStack.fireAutoDismissalFor(link);
    }
  }
}
