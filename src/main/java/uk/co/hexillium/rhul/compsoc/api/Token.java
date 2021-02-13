package uk.co.hexillium.rhul.compsoc.api;

import java.util.Base64;

public class Token {

    final private byte[] token;
    final private long timeout;
    final private String comment;

    public Token(byte[] token, long timeout, String comment){
        this.token = token;
        this.timeout = timeout;
        this.comment = comment;
    }

    public String getToken() {
        return Base64.getEncoder().encodeToString(token);
    }

    public long getTimeout() {
        return timeout;
    }

    public String getComment() {
        return comment;
    }

}
