import test from "node:test";
import assert from "node:assert/strict";
import "../app/src/main/assets/tools/rule-engine.js";

const rules = globalThis.DevToolsRules;
const rule = (kind, pattern, enabled = true) => ({ kind, pattern, enabled });
const list = (kind, entries, selected = true, name = kind) => ({
    id: name,
    name,
    kind,
    selected,
    rules: entries
});
const config = (lists = [], runEverywhere = true) => ({ enabled: true, runEverywhere, lists });

test("enabled defaults apply to ordinary HTTP(S) pages", () => {
    assert.equal(rules.evaluate(config(), "https://example.com/").run, true);
    assert.equal(rules.evaluate(config(), "http://localhost:3000/").run, true);
    assert.equal(rules.evaluate(config(), "about:blank").run, false);
});
test("global disable always wins", () => {
    assert.equal(rules.evaluate({ ...config(), enabled: false }, "https://example.com/").run, false);
});
test("selected blacklist wins over global mode and an AllowList", () => {
    const lists = [
        list("block", [rule("domain", "example.com")]),
        list("allow", [rule("wildcard", "https://*/*")])
    ];
    for (const everywhere of [true, false]) {
        const result = rules.evaluate(config(lists, everywhere), "https://api.example.com/debug");
        assert.equal(result.run, false);
        assert.match(result.reason, /Blacklisted/);
    }
});
test("domain matching uses label boundaries and supports normalization", () => {
    const entry = rule("domain", "ExAmPlE.com");
    assert.equal(rules.matches(entry, "https://EXAMPLE.com./"), true);
    assert.equal(rules.matches(entry, "https://a.example.com:3000/"), true);
    assert.equal(rules.matches(entry, "https://notexample.com/"), false);
    assert.equal(rules.matches(entry, "https://example.com.evil.test/"), false);
});
test("unselected lists and disabled rules have no effect", () => {
    assert.equal(
        rules.evaluate(
            config([list("block", [rule("domain", "example.com")], false)]),
            "https://example.com/"
        ).run,
        true
    );
    assert.equal(
        rules.evaluate(
            config([list("block", [rule("domain", "example.com", false)])]),
            "https://example.com/"
        ).run,
        true
    );
});
test("allow-only mode denies an empty selection and combines lists", () => {
    assert.equal(rules.evaluate(config([], false), "https://example.com/").run, false);
    const lists = [list("allow", [rule("domain", "one.test")]), list("allow", [rule("domain", "two.test")])];
    assert.equal(rules.evaluate(config(lists, false), "https://two.test/").run, true);
    assert.equal(rules.evaluate(config(lists, false), "https://three.test/").run, false);
});
test("exact URLs keep path case and query but ignore fragment and default port", () => {
    const entry = rule("url", "https://example.com:443/Debug?q=1");
    assert.equal(rules.matches(entry, "https://example.com/Debug?q=1#section"), true);
    assert.equal(rules.matches(entry, "https://example.com/debug?q=1"), false);
    assert.equal(rules.matches(entry, "https://example.com/Debug?q=2"), false);
});
test("wildcards are anchored and escape literal punctuation", () => {
    const entry = rule("wildcard", "*://*.example.com/debug/*");
    assert.equal(rules.matches(entry, "http://a.example.com/debug/x"), true);
    assert.equal(rules.matches(entry, "https://example.com/debug/x"), false);
    assert.equal(rules.matches(entry, "https://a.example.com/debug"), false);
    assert.equal(
        rules.matches(rule("wildcard", "https://example.com/file?.js"), "https://example.com/file1.js"),
        true
    );
    assert.equal(
        rules.matches(rule("wildcard", "https://example.com/file.js"), "https://example.com/fileXjs"),
        false
    );
});
test("JavaScript regex and flags work", () => {
    assert.equal(
        rules.matches(
            rule("regex", "/^https:\\/\\/example\\.com\\/debug/i"),
            "https://example.com/DEBUG?x=1"
        ),
        true
    );
    assert.equal(
        rules.matches(rule("regex", "^https://example\\.com/\\d+$"), "https://example.com/42"),
        true
    );
});
test("invalid or expensive active rules fail closed", () => {
    for (const pattern of ["[", "/x/g", "/x/ii", "(a+)+$", "(a|aa)+$", "(?=x)", "(.*){2}"]) {
        assert.throws(() => rules.validate(rule("regex", pattern)));
        assert.equal(
            rules.evaluate(config([list("block", [rule("regex", pattern)])]), "https://example.com/").run,
            false
        );
    }
});
test("pattern and URL limits are enforced", () => {
    assert.throws(() => rules.validate(rule("domain", "a".repeat(513))));
    assert.equal(rules.evaluate(config(), "https://example.com/" + "a".repeat(4096)).run, false);
    assert.throws(() => rules.validate(rule("domain", "https://example.com")));
    assert.throws(() => rules.validate(rule("domain", "example.com:443")));
});
