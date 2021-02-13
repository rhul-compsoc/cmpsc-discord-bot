package uk.co.hexillium.rhul.compsoc.constants;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;

import java.util.regex.Pattern;

public class Regex {

    public static final Pattern JUMP_URL_PATTERN = Message.JUMP_URL_PATTERN;
    public static final Pattern INVITE_URL_PATTERN = Message.INVITE_PATTERN;

    public static final Pattern USER_TAG = User.USER_TAG;
    public static final Pattern USER_MENTION = Message.MentionType.USER.getPattern();
    public static final Pattern EMOTE_MENTION = Message.MentionType.EMOTE.getPattern();
    public static final Pattern CHANNEL_MENTION = Message.MentionType.CHANNEL.getPattern();
    public static final Pattern ROLE_MENTION = Message.MentionType.ROLE.getPattern();



}
