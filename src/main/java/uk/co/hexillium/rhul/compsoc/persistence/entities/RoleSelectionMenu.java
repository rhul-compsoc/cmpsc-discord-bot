package uk.co.hexillium.rhul.compsoc.persistence.entities;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class RoleSelectionMenu {

    List<RoleSelectionCategory> categories;
    long guildID;

    @JsonCreator
    public RoleSelectionMenu(@JsonProperty("categories") List<RoleSelectionCategory> categories, @JsonProperty("guildid") long guildID) {
        this.categories = categories;
        this.guildID = guildID;
    }

    public List<RoleSelectionCategory> getCategories() {
        return categories;
    }

    public long getGuildID() {
        return guildID;
    }
}
