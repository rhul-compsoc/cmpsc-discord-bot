package uk.co.hexillium.rhul.compsoc.persistence.entities;

public class VerificationMessage {

    private final String studentId;
    private final String studentLoginName;
    private final boolean studentVerified;
    private final long studentVerifiedTime;
    private final long studentDetailsSubmitted;
    private final boolean studentDetailsInvalidated;
    private final long studentDiscordSnowflake;
    private final long studentDetailsInvalidatedTime;

    public VerificationMessage(String studentId, String studentLoginName, boolean studentVerified,
                               long studentVerifiedTime, long studentDetailsSubmitted, boolean studentDetailsInvalidated,
                               long studentDiscordSnowflake, long studentDetailsInvalidatedTime) {
        this.studentId = studentId;
        this.studentLoginName = studentLoginName;
        this.studentVerified = studentVerified;
        this.studentVerifiedTime = studentVerifiedTime;
        this.studentDetailsSubmitted = studentDetailsSubmitted;
        this.studentDetailsInvalidated = studentDetailsInvalidated;
        this.studentDiscordSnowflake = studentDiscordSnowflake;
        this.studentDetailsInvalidatedTime = studentDetailsInvalidatedTime;
    }

    public String getStudentId() {
        return studentId;
    }

    public String getStudentLoginName() {
        return studentLoginName;
    }

    public boolean isStudentVerified() {
        return studentVerified;
    }

    public long getStudentVerifiedTime() {
        return studentVerifiedTime;
    }

    public long getStudentDetailsSubmitted() {
        return studentDetailsSubmitted;
    }

    public boolean isStudentDetailsInvalidated() {
        return studentDetailsInvalidated;
    }

    public long getStudentDiscordSnowflake() {
        return studentDiscordSnowflake;
    }

    public long getStudentDetailsInvalidatedTime() {
        return studentDetailsInvalidatedTime;
    }
}
