package uk.co.hexillium.rhul.compsoc.persistence.entities;

import net.dv8tion.jda.api.interactions.components.ButtonStyle;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;

public class RoleSelectionCategory {


    String name;
    @Nullable
    String description;
    @Nullable
    String emoji;
    int max, min;

    long categoryID;
    long guildID;
    long requiredRoleId;

    ButtonStyle style;

    @Nonnull
    List<RoleSelection> roles;

    public RoleSelectionCategory(long guildID, String name, @Nullable String description, @Nullable String emoji, String style, long categoryID,
                                 int min, int max, @Nonnull List<RoleSelection> roles, long requiredRoleId) {
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
        this.requiredRoleId = requiredRoleId;
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

    public long getRequiredRoleId(){
        return requiredRoleId;
    }

    public ButtonStyle getStyle() {
        return style;
    }

    @Nonnull
    public List<RoleSelection> getRoles() {
        return roles;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setDescription(@Nullable String description) {
        this.description = description;
    }

    public void setEmoji(@Nullable String emoji) {
        this.emoji = emoji;
    }

    public void setMax(int max) {
        this.max = max;
    }

    public void setMin(int min) {
        this.min = min;
    }

    public void setRequiredRoleId(long requiredRoleId) {
        this.requiredRoleId = requiredRoleId;
    }

    public void setStyle(String style) {
        try {
            this.style = ButtonStyle.valueOf(style);
        } catch (IllegalArgumentException ex){
            this.style = ButtonStyle.UNKNOWN;
        }
    }

    public void setRoles(@Nonnull List<RoleSelection> roles) {
        this.roles = roles;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        RoleSelectionCategory that = (RoleSelectionCategory) o;

        if (max != that.max) return false;
        if (min != that.min) return false;
        if (categoryID != that.categoryID) return false;
        if (guildID != that.guildID) return false;
        if (requiredRoleId != that.requiredRoleId) return false;
        if (!Objects.equals(name, that.name)) return false;
        if (!Objects.equals(description, that.description)) return false;
        if (!Objects.equals(emoji, that.emoji)) return false;
        if (style != that.style) return false;
        return roles.equals(that.roles);
    }

    @Override
    public int hashCode() {
        int result = name != null ? name.hashCode() : 0;
        result = 31 * result + (description != null ? description.hashCode() : 0);
        result = 31 * result + (emoji != null ? emoji.hashCode() : 0);
        result = 31 * result + max;
        result = 31 * result + min;
        result = 31 * result + (int) (categoryID ^ (categoryID >>> 32));
        result = 31 * result + (int) (guildID ^ (guildID >>> 32));
        result = 31 * result + (int) (requiredRoleId ^ (requiredRoleId >>> 32));
        result = 31 * result + (style != null ? style.hashCode() : 0);
        result = 31 * result + roles.hashCode();
        return result;
    }
}
