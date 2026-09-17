import { beforeEach, describe, expect, it } from "vitest";
import { applyTheme, getTheme } from "./theme";

describe("getTheme", () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it("defaults to dark when nothing is stored — not prefers-color-scheme, a deliberate choice", () => {
    expect(getTheme()).toBe("dark");
  });

  it("returns the stored choice when one exists", () => {
    localStorage.setItem("relayhub-console-theme", "light");
    expect(getTheme()).toBe("light");
  });

  it("falls back to dark for a corrupted/unexpected stored value", () => {
    localStorage.setItem("relayhub-console-theme", "blue");
    expect(getTheme()).toBe("dark");
  });
});

describe("applyTheme", () => {
  beforeEach(() => {
    localStorage.clear();
    document.documentElement.removeAttribute("data-theme");
  });

  it("sets data-theme on the root element and persists the choice", () => {
    applyTheme("light");
    expect(document.documentElement.getAttribute("data-theme")).toBe("light");
    expect(localStorage.getItem("relayhub-console-theme")).toBe("light");
  });

  it("a later call to getTheme sees what applyTheme just persisted", () => {
    applyTheme("light");
    expect(getTheme()).toBe("light");
  });
});
