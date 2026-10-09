/* Shared, side-effect-free rule evaluator. Blacklists always take precedence. */
(function (root) {
    "use strict";
    function canonicalURL(input) {
        const url = new URL(input);
        if (!/^https?:$/.test(url.protocol)) {
            throw new Error("Only HTTP and HTTPS pages are supported.");
        }
        url.hash = "";
        url.username = "";
        url.password = "";
        return url;
    }
    function regex(pattern) {
        let body = pattern;
        let flags = "";
        if (pattern.startsWith("/")) {
            const end = pattern.lastIndexOf("/");
            if (end <= 0) {
                throw new Error("Close the regular expression with /.");
            }
            body = pattern.slice(1, end);
            flags = pattern.slice(end + 1);
        }
        if (!/^[imu]*$/.test(flags) || new Set(flags).size !== flags.length) {
            throw new Error("Use only i, m and u flags, without duplicates.");
        }
        // Keep potentially expensive patterns away from the Safari background thread.
        // This intentionally supports a bounded regex subset, documented in the rule editor.
        if (/\\[1-9]|\(\?[=!<]|\([^)]*[+*{][^)]*\)[+*{]|\([^)]*\|[^)]*\)[+*{]/.test(body)) {
            throw new Error("Backreferences, lookarounds and repeated complex groups are not supported.");
        }
        const repeats = body.match(/(?<!\\)[*+]|\{\d+(?:,\d*)?\}/g) || [];
        if (repeats.length > 4) {
            throw new Error("Use no more than four variable-length repetitions.");
        }
        return new RegExp(body, flags);
    }
    function validate(rule) {
        const pattern = String(rule.pattern || "").trim();
        if (!pattern || pattern.length > 512) {
            throw new Error("Enter a pattern between 1 and 512 characters.");
        }
        switch (rule.kind) {
            case "domain": {
                if (/[\s/*?#@:]/.test(pattern) && !/^\[[0-9a-f:]+\]$/i.test(pattern)) {
                    throw new Error("Enter a hostname such as example.com, without a scheme or path.");
                }
                const host = new URL(`https://${pattern}`).hostname;
                if (!host) {
                    throw new Error("Invalid hostname.");
                }
                break;
            }
            case "url":
                canonicalURL(pattern);
                break;
            case "wildcard":
                if (!/^(?:https?|\*):\/\//.test(pattern)) {
                    throw new Error("Start URL wildcards with https://, http:// or *://.");
                }
                break;
            case "regex":
                regex(pattern);
                break;
            default:
                throw new Error("Unknown rule type.");
        }
        return pattern;
    }
    function matches(rule, input) {
        const url = canonicalURL(input);
        const pattern = validate(rule);
        switch (rule.kind) {
            case "domain": {
                const host = new URL(`https://${pattern}`).hostname.toLowerCase().replace(/\.$/, "");
                const current = url.hostname.toLowerCase().replace(/\.$/, "");
                return current === host || current.endsWith(`.${host}`);
            }
            case "url":
                return url.href === canonicalURL(pattern).href;
            case "wildcard": {
                const escaped = pattern.replace(/[.+^${}()|[\]\\]/g, "\\$&");
                return new RegExp(`^${escaped.replace(/\*/g, ".*").replace(/\?/g, ".")}$`).test(url.href);
            }
            case "regex":
                return regex(pattern).test(url.href.slice(0, 4096));
            default:
                return false;
        }
    }
    function evaluate(config, input) {
        try {
            canonicalURL(input);
            if (String(input).length > 4096) {
                return { run: false, reason: "URL exceeds the 4096-character matching limit." };
            }
            if (!config.enabled) {
                return { run: false, reason: "DevTools is disabled." };
            }
            const lists = (config.lists || []).filter((list) => list.selected);
            const active = lists.flatMap((list) =>
                (list.rules || []).filter((rule) => rule.enabled).map((rule) => ({ list, rule }))
            );
            // Validate ALL active rules before allowing execution; malformed exclusions fail closed.
            active.forEach(({ rule }) => validate(rule));
            const blocked = active.find(({ list, rule }) => list.kind === "block" && matches(rule, input));
            if (blocked) {
                return {
                    run: false,
                    reason: `Blacklisted by ${blocked.list.name}.`,
                    listID: blocked.list.id
                };
            }
            if (config.runEverywhere) {
                return { run: true, reason: "Run on every webpage is enabled." };
            }
            const allowed = active.find(({ list, rule }) => list.kind === "allow" && matches(rule, input));
            return allowed
                ? { run: true, reason: `Allowed by ${allowed.list.name}.`, listID: allowed.list.id }
                : { run: false, reason: "No selected AllowList matches this page." };
        } catch (error) {
            return { run: false, reason: `Invalid rule or URL: ${error.message}`, error: true };
        }
    }
    root.DevToolsRules = { canonicalURL, validate, matches, evaluate };
})(globalThis);
