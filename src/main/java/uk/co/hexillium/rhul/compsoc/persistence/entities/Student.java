package uk.co.hexillium.rhul.compsoc.persistence.entities;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.OffsetDateTime;

public class Student {

    //don't lose any leading zeroes
    private String phoneNumber;  //9-15 chars
    private String studentNo;    //9 chars


    private String firstName;    //2-32
    private String lastName;     //2-32
    private long discordUserID;  //64-bit int
    private String email;        //5-64 chars


    //handled by the database - these are merely markers for the record and should never be changed manually.
    final transient OffsetDateTime creationDate;
    final transient OffsetDateTime modificationDate;

    public Student(String phoneNumber, String studentNo, String firstName, String lastName,
                   long discordUserID, String email, OffsetDateTime creationDate, OffsetDateTime modificationDate) {
        this.phoneNumber = phoneNumber;
        this.studentNo = studentNo;
        this.firstName = firstName;
        this.lastName = lastName;
        this.discordUserID = discordUserID;
        this.email = email;
        this.creationDate = creationDate;
        this.modificationDate = modificationDate;
    }

    /**
     * Gets the phone number of this user.
     * @return the phone number - may not be present
     */
    @Nullable
    public String getPhoneNumber() {
        return phoneNumber;
    }

    /**
     * Gets the Student's ID
     * @return the Student ID of this student
     */
    @Nonnull
    public String getStudentNo() {
        return studentNo;
    }

    /**
     * Gets the Student's first name
     * @return the first name of this student
     */
    @Nonnull
    public String getFirstName() {
        return firstName;
    }

    /**
     * Gets the Student's last name
     * @return the last name of this student
     */
    @Nonnull
    public String getLastName() {
        return lastName;
    }

    /**
     * Gets the ID of the Discord user associated with this Student
     * @return the ID of this Student's Discord user.
     */
    public long getDiscordUserID() {
        return discordUserID;
    }

    /**
     * Gets this student's Email address
     * @return this student's email address - may not be present
     */
    @Nullable
    public String getEmail() {
        return email;
    }

    /**
     * The {@link OffsetDateTime} at which this record in the database was first entered,
     * rather than the creation date of the actual student this record represents.
     * @return the {@link OffsetDateTime} for the creation of this Student
     */
    @Nonnull
    public OffsetDateTime getCreationDate() {
        return creationDate;
    }

    /**
     * The {@link OffsetDateTime} at which this record in the database was last modified
     * @return the {@link OffsetDateTime} for the most recent modification to this record.
     */
    @Nonnull
    public OffsetDateTime getModificationDate() {
        return modificationDate;
    }
}
