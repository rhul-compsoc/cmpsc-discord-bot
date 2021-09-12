# API "Documentation"
The entire API will hide behind the "slug" given in the config.

This slug will prefix all further API routes.

## Authentication

All connections to the API must have a `X-Auth-Token` header.  This is a secret and will grant full access to this API.

The header must contain the token in base-64 encoding.

The `/hello` endpoint can be used to test the configuration.  Successful connections will return `"hi!"`

## Routes

 - GET `/hello` -> returns `"hi!"`
 - GET `/getmembers/<guildid>` -> deprecated; same as GET `/guild/<guildid>/members`
 - `/guild/<guildid>/` contains the following endpoints:
   - GET `/info` -> returns the guild data
   - GET `/members` -> returns information on all members of this guild
   - GET `/member/<memberid or userid>/info` -> returns information on this specific member
   - `/channels/<channelid>` contains the following endpoints:
     - POST `/sendmessage` -> sends a message from the bot to this channel, with the contents.
 - `/games/bindings` contains the following endpoints:
   - `/guild/<guildid>` contains the following endpoints:
     - `/member/<memberid>` contains the following endpoints:
       - GET `/list` -> returns a list of the active game bindings for this user
       - `/game/<gameid>` contains the following endpoint:
         - GET `/list` -> shows the game bindings for this game, for this user
     - `/game/<gameid>` contains the following endpoints:
       - GET `/list` -> show all bindings for this game
       - GET `/id/<gameuserid>` -> show the bindings present in this game, with this game user ID (useful for working backwards from a game account to a Discord account)
   - POST `/create` -> create a new game binding
   - DELETE `/remove` -> delete an existing game binding

## Endpoints Data

### POST /guild/:guildid/channels/:channelid/sendmessage

Example JSON POST:

    {"content": "Hello, world!"}

Example JSON POST:

    {"content": "Hey there! Take an embed!",
    "embeds": [
        {"title": "Hey!",
        "description": "You can include up to 10 embeds per message!"}
    ]}

You can find more information on the JSON format of _embeds_ (not the message content) on the [developer website](https://discord.com/developers/docs/resources/channel#embed-object).


### POST /games/bindings/create

Example JSON POST:

    {"gameId": "minecraft",
    "memberId": "187979032904728576",
    "guildId": "500612695570120704",
    "gameUsername": "Hexillium",
    "gameUserId": "f138eb15-4911-406b-ba4b-29c5c75227ad"}

### DELETE /games/bindings/remove

Example JSON DELETE:

    {"userID":"187979032904728576",
    "bindingID":"10"}