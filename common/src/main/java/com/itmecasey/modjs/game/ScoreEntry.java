package com.itmecasey.modjs.game;

/** One line of a scoreboard objective, as scripts see it: {@code entry.name} and {@code entry.score}. */
public final class ScoreEntry {
    private final String name;
    private final int score;

    public ScoreEntry(String name, int score) {
        this.name = name;
        this.score = score;
    }

    public String getName() {
        return name;
    }

    public int getScore() {
        return score;
    }

    @Override
    public String toString() {
        return name + "=" + score;
    }
}
