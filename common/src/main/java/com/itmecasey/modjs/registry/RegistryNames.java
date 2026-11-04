package com.itmecasey.modjs.registry;

final class RegistryNames {
    private RegistryNames() {
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
