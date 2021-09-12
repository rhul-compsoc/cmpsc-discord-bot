package uk.co.hexillium.rhul.compsoc.persistence.entities;

import javax.annotation.Nullable;

public class RoleSelection {

    long guildID;
    long roleID;

    String name;
    int colour;

    @Nullable String emoji;
    @Nullable String description;

    long categoryID;

    public RoleSelection(long guildID, long roleID, String name, int colour, @Nullable String emoji, @Nullable String description, long categoryID) {
        this.guildID = guildID;
        this.roleID = roleID;
        this.name = name;
        this.colour = colour;
        this.emoji = emoji;
        this.description = description;
        this.categoryID = categoryID;
    }

    public String getName() {
        return name;
    }

    @Nullable
    public String getDescription() {
        return description;
    }

    @Nullable
    public String getEmoji() {
        return emoji;
    }

    public long getRoleID() {
        return roleID;
    }

    public long getGuildID() {
        return guildID;
    }

    public long getCategoryID() {
        return categoryID;
    }

    @Deprecated
    public int getColour() {
        return colour;
    }
}
