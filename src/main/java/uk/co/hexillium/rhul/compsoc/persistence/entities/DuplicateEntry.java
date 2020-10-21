package uk.co.hexillium.rhul.compsoc.persistence.entities;

public class DuplicateEntry {
    long snowflake;
    boolean verified, invalidated;

    public DuplicateEntry(long snowflake, boolean verified, boolean invalidated) {
        this.snowflake = snowflake;
        this.verified = verified;
        this.invalidated = invalidated;
    }

    public long getSnowflake() {
        return snowflake;
    }

    public boolean isVerified() {
        return verified;
    }

    public boolean isInvalidated() {
        return invalidated;
    }
}
