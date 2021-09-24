-- psql
create table job_schedule
(
    job_id          serial
        constraint job_schedule_pk
        primary key,
    target_epoch    bigint not null,
    job_type        text   not null,
    triggered       boolean default false,
    initiated_epoch bigint,
    job_data        jsonb
);

create table student_verification
(
    student_pk                             integer default nextval('student_id_student_pk_seq'::regclass) not null
        constraint student_id_pk
        primary key,
    student_id                             varchar(10)                                                    not null,
    student_login_name                     varchar(128)                                                   not null,
    student_verified                       boolean default false,
    student_verified_time                  bigint  default '-1'::integer,
    student_details_submitted              bigint,
    student_details_invalidated            boolean default false,
    student_type                           integer default 0,
    student_discord_snowflake              bigint                                                         not null,
    student_verification_message_snowflake bigint                                                         not null,
    student_details_invalidated_time       bigint
);

create table member_levels
(
    member_id      bigint            not null,
    guild_id       bigint            not null,
    xp_total       bigint  default 0 not null,
    num_messages   integer default 0,
    hidden         boolean default false,
    recent_xp_gain bigint  default 0,
    constraint member_levels_pk
        primary key (member_id, guild_id)
);

create table api_tokens
(
    token_id        serial
        constraint api_tokens_pk
        primary key,
    token_string    bytea not null,
    token_expiry    bigint  default 0,
    token_comment   varchar(255),
    token_invalided boolean default false
);

create table member_information
(
    member_id  bigint      not null,
    guild_id   bigint      not null,
    nickname   varchar(64),
    username   varchar(64) not null,
    discrim    varchar(4),
    avatar_url varchar(256),
    constraint member_information_pk
        primary key (member_id, guild_id)
);


create table numvember
(
    member_snowflake bigint not null
        constraint numvember_pk
        primary key,
    score            integer default 0
);

create index numvember_correct_answers_index
    on numvember (score desc);

create table messages
(
    channel_id         bigint                not null,
    message_id         bigint                not null,
    modified_timestamp bigint                not null,
    author_id          bigint,
    message_content    varchar(4000),
    attachment_url     varchar(512),
    deleted            boolean default false not null
);

create table game_account_bindings
(
    record_id                serial
        constraint game_account_bindings_pk
        primary key,
    game_id                  varchar(32) not null,
    discord_member_snowflake bigint      not null,
    discord_guild_snowflake  bigint      not null,
    game_username            varchar(128),
    game_user_id             varchar(128),
    user_banned              boolean default false,
    constraint game_account_bindings_snowflake_member_id_fk
        foreign key (discord_guild_snowflake, discord_member_snowflake) references member_information (guild_id, member_id)
);


create table channels
(
    channel_snowflake   bigint
        constraint channels_pk
        unique,
    channel_name        varchar(256),
    channel_permissions bigint,
    channel_description varchar(2000),
    deleted             boolean default false
);

create table guild_settings
(
    guild_id                  bigint      not null
        constraint guild_settings_pk
        primary key,
    guild_prefix              varchar(8) default '!'::character varying,
    guild_mod_role_id         bigint     default '-1'::integer,
    guild_admin_role_id       bigint     default '-1'::integer,
    guild_registered_role_id  bigint     default '-1'::integer,
    guild_log_channel_id      bigint     default '-1'::integer,
    guild_approval_channel_id bigint     default '-1'::integer,
    guild_name                varchar(64) not null,
    guild_icon_url            varchar(256)
);

create table role_categories
(
    guild_id                         bigint       not null
        constraint role_categories_guild_settings_guild_id_fk
        references guild_settings,
    category_name                    varchar(100) not null,
    category_description             text,
    category_emoji                   varchar(32),
    category_button_type             varchar(100),
    category_id                      serial,
    category_min_selection           smallint default 0,
    category_max_selection           smallint default 16,
    category_required_role_snowflake bigint   default '-1'::integer,
    constraint role_categories_pk
        primary key (guild_id, category_id)
);

create table role_options
(
    guildid     bigint       not null
        constraint role_options_guild_settings_guild_id_fk
        references guild_settings
        on delete cascade,
    roleid      bigint       not null,
    role_name   varchar(100) not null,
    colour      integer,
    emoji       varchar(32),
    description varchar(100),
    categoryid  bigint       not null,
    constraint role_options_role_categories_guild_id_category_id_fk
        foreign key (guildid, categoryid) references role_categories
            on update cascade on delete cascade
);

create table poll_data
(
    poll_id     serial
        constraint poll_data_pk
        primary key,
    options     text[] not null,
    started     timestamp with time zone default now(),
    finished    boolean                  default false,
    expires     timestamp with time zone,
    max_options integer                  default 1,
    name        text,
    description text,
    channel_id  bigint,
    guild_id    bigint,
    message_id  bigint
);

create table poll_selections
(
    poll_id_fk integer not null
        constraint poll_selections_poll_data_poll_id_fk
        references poll_data,
    member_id  bigint  not null,
    selections integer not null,
    time_made  timestamp with time zone default now(),
    constraint poll_selections_pk
        primary key (poll_id_fk, member_id)
);


create index poll_selections_poll_id_fk_index
    on poll_selections (poll_id_fk desc);

create unique index poll_selections_poll_id_fk_member_id_uindex
    on poll_selections (poll_id_fk, member_id);

