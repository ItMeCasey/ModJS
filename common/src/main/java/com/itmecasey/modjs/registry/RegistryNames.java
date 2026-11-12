package com.itmecasey.modjs.registry;

final class RegistryNames {
    /** Where an id without a namespace goes. */
    static final String DEFAULT_NAMESPACE = "modjs";

    private RegistryNames() {
    }

    /** A registry id split into its namespace and path. */
    record Id(String namespace, String path) {
        String full() {
            return namespace + ":" + path;
        }
    }

    /** Checks a namespace: lowercase letters, digits, {@code _ . -}, and not {@code minecraft}. */
    static String checkNamespace(String namespace) {
        if (namespace == null || !namespace.matches("[a-z0-9_.-]+")) {
            throw new IllegalArgumentException("Invalid namespace '" + namespace + "': use lowercase letters, digits, _ . -");
        }
        if (namespace.equals("minecraft")) {
            throw new IllegalArgumentException("The namespace 'minecraft' is reserved; use your own, for example 'mypack:ruby'");
        }
        return namespace;
    }

    /** Splits {@code "mypack:ruby"} or {@code "ruby"} (then in {@code defaultNamespace}) and validates both parts. */
    static Id parse(String raw, String defaultNamespace) {
        if (raw == null || raw.isEmpty()) {
            throw new IllegalArgumentException("An id cannot be empty");
        }
        int colon = raw.indexOf(':');
        String namespace = colon < 0 ? defaultNamespace : raw.substring(0, colon);
        String path = colon < 0 ? raw : raw.substring(colon + 1);
        if (path.contains(":")) {
            throw new IllegalArgumentException("Invalid id '" + raw + "': only one ':' is allowed");
        }
        checkNamespace(namespace);
        if (!path.matches("[a-z0-9_./-]+")) {
            throw new IllegalArgumentException("Invalid id '" + raw + "': the path uses lowercase letters, digits, _ . / -");
        }
        return new Id(namespace, path);
    }

    static Id parse(String raw) {
        return parse(raw, DEFAULT_NAMESPACE);
    }

    /** {@code "ruby_block"} becomes {@code "Ruby Block"}. */
    static String titleCase(String id) {
        StringBuilder out = new StringBuilder();
        for (String word : id.split("[_/]")) {
            if (word.isEmpty()) {
                continue;
            }
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
    }
}
