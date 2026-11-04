package com.itmecasey.modjs.game;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

import java.util.ArrayList;
import java.util.List;

/**
 * The {@code Scoreboard} global: objectives, scores, display slots and teams. A holder is a name (a player name, or any text
 * such as {@code '#total'}) or a player or other entity, whose scoreboard name is used.
 */
public final class ScoreboardBinding {
    private static Scoreboard board() {
        MinecraftServer server = ServerState.get();
        if (server == null) {
            throw new IllegalStateException("Scoreboard can only be used while a world is loaded");
        }
        return server.getScoreboard();
    }

    private static ScoreHolder holder(Object value) {
        if (value instanceof Entity entity) {
            return ScoreHolder.forNameOnly(entity.getScoreboardName());
        }
        if (value instanceof CharSequence text) {
            return ScoreHolder.forNameOnly(text.toString());
        }
        throw new IllegalArgumentException("Expected a player or a name, got " + value);
    }

    private static String nameOf(Object value) {
        return holder(value).getScoreboardName();
    }

    private static Objective objective(String name) {
        Objective objective = board().getObjective(name);
        if (objective == null) {
            throw new IllegalArgumentException("No objective '" + name + "'. Scoreboard.addObjective('" + name + "') creates one");
        }
        return objective;
    }

    private static PlayerTeam team(String name) {
        PlayerTeam team = board().getPlayerTeam(name);
        if (team == null) {
            throw new IllegalArgumentException("No team '" + name + "'. Scoreboard.addTeam('" + name + "') creates one");
        }
        return team;
    }

    // ---- objectives ----

    public List<String> objectives() {
        return new ArrayList<>(board().getObjectiveNames());
    }

    public boolean hasObjective(String name) {
        return board().getObjective(name) != null;
    }

    /** Creates an objective that scripts and commands set by hand. */
    public void addObjective(String name) {
        addObjective(name, name, "dummy");
    }

    public void addObjective(String name, String displayName) {
        addObjective(name, displayName, "dummy");
    }

    /** {@code criteria} is a vanilla criterion such as {@code dummy}, {@code health} or {@code minecraft.killed:minecraft.zombie}. */
    public void addObjective(String name, String displayName, String criteria) {
        Scoreboard board = board();
        if (board.getObjective(name) != null) {
            return;
        }
        ObjectiveCriteria parsed = ObjectiveCriteria.byName(criteria).orElseThrow(() -> new IllegalArgumentException("Unknown criterion '" + criteria + "'"));
        board.addObjective(name, parsed, Component.literal(displayName), parsed.getDefaultRenderType(), true, null);
    }

    public void removeObjective(String name) {
        Scoreboard board = board();
        Objective objective = board.getObjective(name);
        if (objective != null) {
            board.removeObjective(objective);
        }
    }

    public void setObjectiveDisplayName(String name, String displayName) {
        objective(name).setDisplayName(Component.literal(displayName));
    }

    // ---- scores ----

    /** The score, or 0 when the holder has none. */
    public int get(Object holder, String objective) {
        ReadOnlyScoreInfo info = board().getPlayerScoreInfo(holder(holder), objective(objective));
        return info == null ? 0 : info.value();
    }

    public boolean has(Object holder, String objective) {
        return board().getPlayerScoreInfo(holder(holder), objective(objective)) != null;
    }

    public void set(Object holder, String objective, int value) {
        board().getOrCreatePlayerScore(holder(holder), objective(objective)).set(value);
    }

    /** Adds to the score (a negative number subtracts) and returns the new value. */
    public int add(Object holder, String objective, int amount) {
        return board().getOrCreatePlayerScore(holder(holder), objective(objective)).add(amount);
    }

    /** Removes every score of the holder. */
    public void reset(Object holder) {
        board().resetAllPlayerScores(holder(holder));
    }

    public void reset(Object holder, String objective) {
        board().resetSinglePlayerScore(holder(holder), objective(objective));
    }

    /** Every score of an objective, highest first. */
    public List<ScoreEntry> scores(String objective) {
        List<ScoreEntry> out = new ArrayList<>();
        board().listPlayerScores(objective(objective)).forEach(entry -> out.add(new ScoreEntry(entry.owner(), entry.value())));
        out.sort((a, b) -> Integer.compare(b.getScore(), a.getScore()));
        return out;
    }

    /** Every name that has a score. */
    public List<String> holders() {
        List<String> out = new ArrayList<>();
        board().getTrackedPlayers().forEach(holder -> out.add(holder.getScoreboardName()));
        return out;
    }

    // ---- display ----

    private static DisplaySlot slot(String name) {
        return switch (name.toLowerCase().replace("_", "")) {
            case "sidebar" -> DisplaySlot.SIDEBAR;
            case "list", "tablist" -> DisplaySlot.LIST;
            case "belowname" -> DisplaySlot.BELOW_NAME;
            default -> throw new IllegalArgumentException("Unknown display slot '" + name + "'; use sidebar, list or below_name");
        };
    }

    /** Shows an objective in the sidebar, the player list or under names. Pass null to clear the slot. */
    public void setDisplay(String slot, String objective) {
        board().setDisplayObjective(slot(slot), objective == null ? null : objective(objective));
    }

    /** The objective shown in a slot, or null. */
    public String getDisplay(String slot) {
        Objective objective = board().getDisplayObjective(slot(slot));
        return objective == null ? null : objective.getName();
    }

    // ---- teams ----

    public List<String> teams() {
        return new ArrayList<>(board().getTeamNames());
    }

    public void addTeam(String name) {
        if (board().getPlayerTeam(name) == null) {
            board().addPlayerTeam(name);
        }
    }

    public void removeTeam(String name) {
        PlayerTeam team = board().getPlayerTeam(name);
        if (team != null) {
            board().removePlayerTeam(team);
        }
    }

    public void joinTeam(Object holder, String team) {
        board().addPlayerToTeam(nameOf(holder), team(team));
    }

    public void leaveTeam(Object holder) {
        board().removePlayerFromTeam(nameOf(holder));
    }

    /** The name of the holder's team, or null. */
    public String teamOf(Object holder) {
        PlayerTeam team = board().getPlayersTeam(nameOf(holder));
        return team == null ? null : team.getName();
    }

    public List<String> teamMembers(String team) {
        return new ArrayList<>(team(team).getPlayers());
    }

    /** Sets a team's color by name, such as {@code red} or {@code dark_aqua}. */
    public void setTeamColor(String team, String color) {
        ChatFormatting formatting = ChatFormatting.getByName(color);
        if (formatting == null || !formatting.isColor()) {
            throw new IllegalArgumentException("Unknown team color '" + color + "'");
        }
        team(team).setColor(formatting);
    }

    public void setTeamPrefix(String team, String text) {
        team(team).setPlayerPrefix(Component.literal(text));
    }

    public void setTeamSuffix(String team, String text) {
        team(team).setPlayerSuffix(Component.literal(text));
    }

    public void setFriendlyFire(String team, boolean allowed) {
        team(team).setAllowFriendlyFire(allowed);
    }
}
