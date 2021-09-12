package uk.co.hexillium.rhul.compsoc.persistence.entities;

import net.dv8tion.jda.api.interactions.components.ButtonStyle;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

public class RoleSelectionCategory {


    String name;
    @Nullable
    String description;
    @Nullable
    String emoji;
    int max, min;

    long categoryID;
    long guildID;

    ButtonStyle style;

    @Nonnull
    List<RoleSelection> roles;

    public RoleSelectionCategory(long guildID, String name, @Nullable String description, @Nullable String emoji, String style, long categoryID,
                                 int min, int max, @Nonnull List<RoleSelection> roles) {
        this.name = name;
        this.description = description;
        this.emoji = emoji;
        this.max = max;
        this.min = min;
        this.categoryID = categoryID;
        this.guildID = guildID;
        try {
            this.style = ButtonStyle.valueOf(style);
        } catch (IllegalArgumentException ex){
            this.style = ButtonStyle.UNKNOWN;
        }
        this.roles = roles;
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

    public int getMax() {
        return max;
    }

    public int getMin() {
        return min;
    }

    public long getCategoryID() {
        return categoryID;
    }

    public long getGuildID() {
        return guildID;
    }

    public ButtonStyle getStyle() {
        return style;
    }

    @Nonnull
    public List<RoleSelection> getRoles() {
        return roles;
    }
}
