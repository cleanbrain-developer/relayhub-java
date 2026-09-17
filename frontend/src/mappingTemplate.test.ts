import { describe, expect, it } from "vitest";
import { buildTemplate, parseTemplate } from "./mappingTemplate";

describe("parseTemplate", () => {
  it("unwraps ${$.jsonPath} placeholders into a JSONPath row", () => {
    const { rows, parseable } = parseTemplate('{"flightNo":"${$.flightNo}"}');
    expect(parseable).toBe(true);
    expect(rows).toEqual([{ field: "flightNo", jsonPath: "$.flightNo" }]);
  });

  it("keeps a non-placeholder value as a literal row", () => {
    const { rows } = parseTemplate('{"active":"true"}');
    expect(rows).toEqual([{ field: "active", jsonPath: "true" }]);
  });

  it("treats a nested object value's placeholder pattern as a coincidental literal, not JSONPath", () => {
    // Object.entries + String(raw) on a non-string value never matches PLACEHOLDER_RE, so a
    // nested object round-trips as its JSON string form rather than throwing.
    const { rows } = parseTemplate('{"meta":{"nested":true}}');
    expect(rows).toEqual([{ field: "meta", jsonPath: "[object Object]" }]);
  });

  it("treats an empty string as an empty (still-editable) template, not a parse failure", () => {
    const { rows, parseable } = parseTemplate("");
    expect(parseable).toBe(true);
    expect(rows).toEqual([]);
  });

  it("flags invalid JSON as unparseable", () => {
    const { parseable, rows } = parseTemplate("{not json");
    expect(parseable).toBe(false);
    expect(rows).toEqual([]);
  });

  it("flags a JSON array as unparseable — a target payload template must be an object", () => {
    const { parseable } = parseTemplate("[1,2,3]");
    expect(parseable).toBe(false);
  });
});

describe("buildTemplate", () => {
  it("wraps a JSONPath-looking value ($ prefix) in the ${...} placeholder syntax", () => {
    expect(buildTemplate([{ field: "flightNo", jsonPath: "$.flightNo" }])).toBe('{"flightNo":"${$.flightNo}"}');
  });

  it("writes a non-$ value through as a literal, unwrapped", () => {
    expect(buildTemplate([{ field: "active", jsonPath: "true" }])).toBe('{"active":"true"}');
  });

  it("trims surrounding whitespace before deciding whether a value is a JSONPath", () => {
    expect(buildTemplate([{ field: "flightNo", jsonPath: "  $.flightNo  " }])).toBe('{"flightNo":"${$.flightNo}"}');
  });

  it("skips a row with no field name — an unmapped row shouldn't produce a blank-key entry", () => {
    expect(buildTemplate([{ field: "", jsonPath: "$.flightNo" }])).toBe("{}");
  });

  it("round-trips through parseTemplate", () => {
    const original = [
      { field: "flightNo", jsonPath: "$.flightNo" },
      { field: "status", jsonPath: "$.status" },
      { field: "active", jsonPath: "true" },
    ];
    const { rows } = parseTemplate(buildTemplate(original));
    expect(rows).toEqual(original);
  });
});
