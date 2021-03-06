package dao;

import dao.entities.CommandStats;
import dao.entities.GuildProperties;
import dao.entities.LastFMData;
import dao.entities.UsersWrapper;
import dao.exceptions.InstanceNotFoundException;
import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.Nullable;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.sql.Connection;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;

interface UserGuildDao {

    void createGuild(Connection con, long guildId);

    void insertUserData(Connection con, LastFMData lastFMData);

    void insertTempUser(Connection con, long discordId, String token);

    LastFMData findLastFmData(Connection con, long discordId) throws dao.exceptions.InstanceNotFoundException;

    List<Long> guildsFromUser(Connection connection, long userId);

    MultiValuedMap<Long, Long> getWholeUserGuild(Connection connection);

    void updateLastFmData(Connection con, LastFMData lastFMData);

    void removeUser(Connection con, Long discordId);

    void removeUserGuild(Connection con, long discordId, long guildId);

    List<UsersWrapper> getAll(Connection connection, long guildId);

    List<UsersWrapper> getAllNonPrivate(Connection connection, long guildId);

    void addGuild(Connection con, long userId, long guildId);

    void addLogo(Connection con, long guildID, BufferedImage image);

    void removeLogo(Connection connection, long guildId);

    InputStream findLogo(Connection connection, long guildID);

    long getDiscordIdFromLastFm(Connection connection, String lastFmName) throws InstanceNotFoundException;

    long getDiscordIdFromLastFm(Connection connection, String lastFmName, long guildId) throws InstanceNotFoundException;


    LastFMData findByLastFMId(Connection connection, String lastFmID) throws InstanceNotFoundException;


    List<UsersWrapper> getAll(Connection connection);

    void removeRateLimit(Connection connection, long discordId);

    void upsertRateLimit(Connection connection, long discordId, float queriesPerSecond);

    void insertServerDisabled(Connection connection, long discordId, String commandName);

    void insertChannelCommandStatus(Connection connection, long discordId, long channelId, String commandName, boolean enabled);

    void deleteChannelCommandStatus(Connection connection, long discordId, long channelId, String commandName);

    void deleteServerCommandStatus(Connection connection, long discordId, String commandName);


    MultiValuedMap<Long, String> initServerCommandStatuses(Connection connection);

    MultiValuedMap<Pair<Long, Long>, String> initServerChannelsCommandStatuses(Connection connection, boolean enabled);

    void setUserProperty(Connection connection, long discordId, String additional_embed, boolean chartEmbed);

    void setGuildProperty(Connection connection, long guildId, String property, boolean value);

    <T extends Enum<T>> void setUserProperty(Connection connection, long discordId, String propertyName, T value);


    GuildProperties getGuild(Connection connection, long discordId) throws InstanceNotFoundException;

    LastFMData findLastFmData(Connection connection, long discordID, long guildId) throws InstanceNotFoundException;

    <T extends Enum<T>> void setGuildProperty(Connection connection, long discordId, String propertyName, @Nullable T value);

    void setChartDefaults(Connection connection, long discordId, int x, int y);

    void serverBlock(Connection connection, long discordId, long guildId);

    boolean isUserServerBanned(Connection connection, long userId, long guildID);

    void serverUnblock(Connection connection, long discordId, long guildId);

    long getNPRaw(Connection connection, long discordId);

    void setNpRaw(Connection connection, long discordId, long raw);

    long getServerNPRaw(Connection connection, long guildId);

    void setServerNpRaw(Connection connection, long guild_id, long raw);

    void setTimezoneUser(Connection connection, TimeZone timeZone, long idLong);

    TimeZone getTimezone(Connection connection, long userId);

    Set<Long> getGuildsWithDeletableMessages(Connection connection);

    Set<Long> getGuildsDontRespondOnErrros(Connection connection);


    void changeDiscordId(Connection connection, long userId, String lastFmID);

    CommandStats getCommandStats(long discordId, Connection connection);

    Set<LastFMData> findScrobbleableUsers(Connection connection, long guildId);
}
