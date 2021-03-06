package dao;

import dao.entities.*;
import dao.exceptions.ChuuServiceException;
import org.apache.commons.lang3.tuple.Pair;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static dao.UpdaterDaoImpl.preparePlaceHolders;

public class SQLQueriesDaoImpl extends BaseDAO implements SQLQueriesDao {


    @Override
    public void getGlobalRank(Connection connection, String lastfmId) {
        // TODO ONE DAY
    }

    @Override
    public UniqueWrapper<ArtistPlays> getGlobalCrowns(Connection connection, String lastfmId, int threshold, boolean includeBottedUsers, long ownerId) {
        List<ArtistPlays> returnList = new ArrayList<>();
        long discordId = 0;

        @Language("MariaDB") String queryString = "SELECT c.name , b.discord_id , playnumber AS orden" +
                " FROM  scrobbled_artist  a" +
                " JOIN user b ON a.lastfm_id = b.lastfm_id" +
                " JOIN artist c ON " +
                " a.artist_id = c.id" +
                " WHERE  a.lastfm_id = ?" +
                " AND playnumber >= ?" +
                " AND  playnumber >= ALL" +
                "       (SELECT max(b.playnumber) " +
                " FROM " +
                "(SELECT in_a.artist_id,in_a.playnumber" +
                " FROM scrobbled_artist in_a  " +
                " JOIN " +
                " user in_b" +
                " ON in_a.lastfm_id = in_b.lastfm_id" +
                " where ? or not in_b.botted_account " +
                "   ) AS b" +
                " WHERE b.artist_id = a.artist_id" +
                " GROUP BY artist_id)" +
                " ORDER BY orden DESC";

        try (PreparedStatement preparedStatement = connection.prepareStatement(queryString)) {
            preparedStatement.setString(1, lastfmId);
            preparedStatement.setInt(2, threshold);
            preparedStatement.setBoolean(3, includeBottedUsers);
            //preparedStatement.setLong(4, ownerId);


            ResultSet resultSet = preparedStatement.executeQuery();


            while (resultSet.next()) {

                String artist = resultSet.getString("c.name");
                int plays = resultSet.getInt("orden");
                returnList.add(new ArtistPlays(artist, plays));
                // TODO
                discordId = resultSet.getLong("b.discord_id");

            }
        } catch (SQLException e) {
            logger.warn(e.getMessage(), e);
            throw new ChuuServiceException(e);
        }
        return new UniqueWrapper<>(returnList.size(), discordId, lastfmId, returnList);

    }

    @Override
    public UniqueWrapper<ArtistPlays> getGlobalUniques(Connection connection, String lastfmId) {

        @Language("MariaDB") String queryString = "SELECT a.name, temp.playnumber, temp.lastfm_id, temp.discord_id " +
                "FROM(  " +
                "       SELECT artist_id, playnumber, a.lastfm_id ,b.discord_id" +
                "       FROM scrobbled_artist a JOIN user b " +
                "       ON a.lastfm_id = b.lastfm_id " +
                "       WHERE  a.playnumber > 2 " +
                "       GROUP BY a.artist_id " +
                "       HAVING count( *) = 1) temp " +
                " JOIN artist a ON temp.artist_id = a.id " +
                "WHERE temp.lastfm_id = ? AND temp.playnumber > 1 " +
                " ORDER BY temp.playnumber DESC ";

        try (PreparedStatement preparedStatement = connection.prepareStatement(queryString)) {
            int i = 1;
            preparedStatement.setString(i, lastfmId);
            ResultSet resultSet = preparedStatement.executeQuery();


            List<ArtistPlays> returnList = new ArrayList<>();
            long discordId = 0;
            while (resultSet.next()) {
                discordId = resultSet.getLong("temp.discord_id");
                String name = resultSet.getString("a.name");
                int countA = resultSet.getInt("temp.playNumber");

                returnList.add(new ArtistPlays(name, countA));

            }
            return new UniqueWrapper<>(returnList.size(), discordId, lastfmId, returnList);


        } catch (SQLException e) {
            logger.warn(e.getMessage(), e);
        }
        return null;
    }

    @Override
    public ResultWrapper<ArtistPlays> getArtistPlayCount(Connection connection, Long guildId) {
        @Language("MariaDB") String queryBody =
                "FROM  (SELECT artist_id,playnumber " +
                        "FROM scrobbled_artist a" +
                        " JOIN user b  " +
                        " ON a.lastfm_id = b.lastfm_id " +
                        " JOIN user_guild c " +
                        " ON b.discord_id = c.discord_id" +
                        " WHERE c.guild_id = ?) main" +
                        " JOIN artist b ON" +
                        " main.artist_id = b.id ";

        String normalQuery = "SELECT b.name, sum(playNumber) AS orden " + queryBody + " GROUP BY main.artist_id ORDER BY orden DESC  Limit 200";
        String countQuery = "Select sum(playnumber) " + queryBody;
        return getArtistPlaysResultWrapper(connection, guildId, normalQuery, countQuery);
    }

    @NotNull
    private ResultWrapper<ArtistPlays> getArtistPlaysResultWrapper(Connection connection, Long guildId, String normalQuery, String countQuery) {
        try (PreparedStatement preparedStatement2 = connection.prepareStatement(countQuery)) {
            preparedStatement2.setLong(1, guildId);

            ResultSet resultSet = preparedStatement2.executeQuery();
            if (!resultSet.next()) {
                throw new ChuuServiceException();
            }
            int rows = resultSet.getInt(1);
            try (PreparedStatement preparedStatement = connection.prepareStatement(normalQuery)) {
                preparedStatement.setLong(1, guildId);
                return getArtistPlaysResultWrapper(rows, preparedStatement);
            }
        } catch (SQLException e) {
            logger.warn(e.getMessage(), e);
            throw new ChuuServiceException(e);
        }
    }

    @Override
    public ResultWrapper<ArtistPlays> getArtistsFrequencies(Connection connection, Long guildId) {
        @Language("MariaDB") String queryBody =
                "FROM  (SELECT artist_id " +
                        "FROM scrobbled_artist a" +
                        " JOIN user b  " +
                        " ON a.lastfm_id = b.lastfm_id " +
                        " JOIN user_guild c " +
                        " ON b.discord_id = c.discord_id" +
                        " WHERE c.guild_id = ?) main" +
                        " JOIN artist b ON" +
                        " main.artist_id = b.id ";

        String normalQuery = "SELECT b.name, count(*) AS orden " + queryBody + " GROUP BY b.id ORDER BY orden DESC  Limit 200";
        String countQuery = "Select count(*) " + queryBody;
        return getArtistPlaysResultWrapper(connection, guildId, normalQuery, countQuery);
    }

    @Override
    public List<TagPlays> getServerTags(Connection connection, Long guildId, boolean doCount) {
        @Language("MariaDB") String query =
                "SELECT tag, " + (doCount ? "count(*)" : "sum(a.playnumber)") + " AS orden " +
                        "FROM scrobbled_artist a" +
                        " JOIN user b  " +
                        " ON a.lastfm_id = b.lastfm_id " +
                        " JOIN user_guild c " +
                        " ON b.discord_id = c.discord_id" +
                        " join artist_tags d on a.artist_id = d.artist_id " +
                        " WHERE c.guild_id = ? " +
                        " GROUP BY tag ORDER BY orden DESC Limit 200";

        List<TagPlays> returnList = new ArrayList<>();
        try (PreparedStatement preparedStatement = connection.prepareStatement(query)) {
            preparedStatement.setLong(1, guildId);
            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                String name = resultSet.getString("tag");
                int count = resultSet.getInt("orden");
                returnList.add(new TagPlays(name, count));
            }
        } catch (SQLException e) {
            logger.warn(e.getMessage(), e);
            throw new ChuuServiceException(e);
        }

        return returnList;
    }

    @NotNull
    private ResultWrapper<ArtistPlays> getArtistPlaysResultWrapper(int rows, PreparedStatement preparedStatement) throws SQLException {
        ResultSet resultSet;
        resultSet = preparedStatement.executeQuery();
        List<ArtistPlays> returnList = new ArrayList<>();
        while (resultSet.next()) {
            String name = resultSet.getString("name");
            int count = resultSet.getInt("orden");
            returnList.add(new ArtistPlays(name, count));
        }

        return new ResultWrapper<>(rows, returnList);
    }


    @Override
    public ResultWrapper<ArtistPlays> getGlobalArtistPlayCount(Connection connection) {
        @Language("MariaDB") String queryString =
                "FROM  scrobbled_artist a" +
                        " JOIN artist b " +
                        " ON a.artist_id = b.id ";


        String normalQuery = "SELECT b.name, sum(playNumber) AS orden " + queryString + "     GROUP BY artist_id  ORDER BY orden DESC  Limit 200";
        String countQuery = "Select sum(playNumber) " + queryString;

        return getArtistPlaysResultWrapper(connection, normalQuery, countQuery);

    }

    @NotNull
    private ResultWrapper<ArtistPlays> getArtistPlaysResultWrapper(Connection connection, String normalQuery, String countQuery) {
        int rows;
        try (PreparedStatement preparedStatement2 = connection.prepareStatement(countQuery)) {

            ResultSet resultSet = preparedStatement2.executeQuery();
            if (!resultSet.next()) {
                throw new ChuuServiceException();
            }
            rows = resultSet.getInt(1);
            try (PreparedStatement preparedStatement = connection.prepareStatement(normalQuery)) {


                return getArtistPlaysResultWrapper(rows, preparedStatement);
            }
        } catch (SQLException e) {
            logger.warn(e.getMessage(), e);
            throw new ChuuServiceException(e);
        }
    }

    @Override
    public ResultWrapper<ArtistPlays> getGlobalArtistFrequencies(Connection connection) {
        @Language("MariaDB") String queryString =
                "FROM  scrobbled_artist a" +
                        " JOIN artist b " +
                        " ON a.artist_id = b.id ";


        String normalQuery = "SELECT b.name, count(*) AS orden " + queryString + "     GROUP BY artist_id  ORDER BY orden DESC  Limit 200";
        String countQuery = "Select count(*)" + queryString;
        return getArtistPlaysResultWrapper(connection, normalQuery, countQuery);

    }

    @Override
    public List<ScrobbledArtist> getAllUsersArtist(Connection connection, long discordId) {
        List<ScrobbledArtist> scrobbledArtists = new ArrayList<>();
        String queryString = " Select * from scrobbled_artist a join artist b on a.artist_id = b.id join user" +
                " c on a.lastfm_id = c.lastfm_id where c.lastfmid = ? order by a.plays desc";
        try (
                PreparedStatement preparedStatement = connection.prepareStatement(queryString)) {
            preparedStatement.setLong(1, discordId);

            ResultSet resultSet = preparedStatement.executeQuery();

            while (resultSet.next()) {
                String artistName = resultSet.getString("b.name");
                String artistUrl = resultSet.getString("b.url");
                int plays = resultSet.getInt("a.playNumber");
                ScrobbledArtist e = new ScrobbledArtist(null, artistName, plays);
                e.setUrl(artistUrl);
                scrobbledArtists.add(e);
            }
        } catch (SQLException e) {
            throw new ChuuServiceException(e);
        }
        return scrobbledArtists;
    }

    @Override
    public List<LbEntry> matchingArtistCount(Connection connection, long userId, long guildId, Long threshold) {

        String queryString = """
                        select discord_id,lastfm_id,count(*) as orden
                        from
                                (select artist_id,a.lastfm_id,b.discord_id from scrobbled_artist a
                                        join user b
                                        on a.lastfm_id = b.lastfm_id
                                        join user_guild c
                                        on c.discord_id = b.discord_id
                                        where c.guild_id = ?
                                and b.discord_id != ?) main


                        where main.artist_id in
                        (Select artist_id from scrobbled_artist a join user b on a.lastfm_id = b.lastfm_id where
                        discord_id = ?
                	)
                        group by lastfm_id,discord_id
                        order by orden desc;
                """;
        try (PreparedStatement preparedStatement = connection.prepareStatement(queryString)) {
            int i = 1;
            preparedStatement.setLong(i++, guildId);
            preparedStatement.setLong(i++, userId);
            preparedStatement.setLong(i, userId);

            List<LbEntry> returnedList = new ArrayList<>();

            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) { //&& (j < 10 && j < rows)) {
                String lastfmId = resultSet.getString("lastfm_id");
                long discordId = resultSet.getLong("discord_id");
                int crowns = resultSet.getInt("orden");

                returnedList.add(new ArtistLbEntry(lastfmId, discordId, crowns));


            }
            return returnedList;
        } catch (SQLException e) {
            logger.warn(e.getMessage(), e);
            throw new ChuuServiceException((e));
        }
    }

    @Override
    public List<VotingEntity> getAllArtistImages(Connection connection, long artistId) {
        List<VotingEntity> returnedList = new ArrayList<>();
        String queryString = " Select a.id,a.url,a.score,a.discord_id,a.added_date,b.name,b.id,(select count(*) from vote where alt_id = a.id) as totalVotes from alt_url a join artist b on a.artist_id = b.id  where a.artist_id = ? order by a.score desc , added_date asc ";
        try (
                PreparedStatement preparedStatement = connection.prepareStatement(queryString)) {
            preparedStatement.setLong(1, artistId);

            ResultSet resultSet = preparedStatement.executeQuery();

            while (resultSet.next()) {
                String artistName = resultSet.getString("b.name");
                String artistUrl = resultSet.getString("a.url");
                long owner = resultSet.getLong("a.discord_id");
                artistId = resultSet.getLong("b.id");
                long urlId = resultSet.getLong("a.id");

                int votes = resultSet.getInt("a.score");
                int totalVotes = resultSet.getInt("totalVotes");
                Timestamp date = resultSet.getTimestamp("a.added_date");
                returnedList.add(new VotingEntity(artistName, artistUrl, date.toLocalDateTime(), owner, artistId, votes, totalVotes, urlId));
            }
        } catch (SQLException e) {
            throw new ChuuServiceException(e);
        }
        return returnedList;
    }

    @Override
    public Boolean hasUserVotedImage(Connection connection, long urlId, long discordId) {
        String queryString = "Select ispositive from vote where alt_id = ? and discord_id = ? ";
        try (PreparedStatement preparedStatement = connection.prepareStatement(queryString)) {
            preparedStatement.setLong(1, urlId);
            preparedStatement.setLong(2, discordId);
            ResultSet resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) {
                return resultSet.getBoolean(1);
            }
            return null;

        } catch (SQLException e) {
            throw new ChuuServiceException(e);
        }
    }


    @Override
    public List<String> getArtistAliases(Connection connection, long artistId) {
        @Language("MariaDB") String queryString = "SELECT alias FROM corrections WHERE artist_id = ? ";
        List<String> returnList = new ArrayList<>();
        try (PreparedStatement preparedStatement = connection.prepareStatement(queryString)) {
            int i = 1;
            preparedStatement.setLong(i, artistId);
            ResultSet resultSet = preparedStatement.executeQuery();

            while (resultSet.next()) { //&& (j < 10 && j < rows)) {
                String name = resultSet.getString("alias");
                returnList.add(name);
            }
            return returnList;

        } catch (SQLException e) {
            logger.warn(e.getMessage(), e);
            throw new ChuuServiceException(e);
        }


    }

    @Override
    public long getArtistPlays(Connection connection, Long guildID, long artistId) {
        @Language("MariaDB") String queryString = "SELECT sum(playnumber) FROM scrobbled_artist a " +
                "JOIN user b " +
                "ON a.lastfm_id = b.lastfm_id ";
        if (guildID != null) {
            queryString += " JOIN user_guild c " +
                    " ON b.discord_id = c.discord_id ";
        }
        queryString += " WHERE  artist_id = ? ";
        if (guildID != null) {
            queryString += " and c.guild_id = ?";
        }
        try (PreparedStatement preparedStatement = connection.prepareStatement(queryString)) {
            preparedStatement.setLong(1, artistId);
            if (guildID != null) {
                preparedStatement.setLong(2, guildID);
            }
            ResultSet resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) {
                return resultSet.getLong(1);
            }
            return 0;
        } catch (SQLException e) {
            throw new ChuuServiceException(e);
        }
    }

    @Override
    public long getArtistFrequencies(Connection connection, Long guildID, long artistId) {

        @Language("MariaDB") String queryBody =
                "Select count(*) from  scrobbled_artist where artist_id = ? \n";

        if (guildID != null) queryBody += """
                and lastfm_id in (
                (SELECT lastfm_id from user b\s
                 JOIN user_guild c\s
                 ON b.discord_id = c.discord_id
                 WHERE c.guild_id = ?))""";
        else queryBody += "";

        try (PreparedStatement preparedStatement2 = connection.prepareStatement(queryBody)) {
            preparedStatement2.setLong(1, artistId);
            if (guildID != null)
                preparedStatement2.setLong(2, guildID);

            ResultSet resultSet = preparedStatement2.executeQuery();
            if (!resultSet.next()) {
                return 0;
            }
            return resultSet.getLong(1);

        } catch (SQLException e) {
            throw new ChuuServiceException(e);
        }
    }

    @Override
    public int getGuildCrownThreshold(Connection connection, long guildID) {
        @Language("MariaDB") String queryBody = "SELECT crown_threshold FROM guild WHERE guild_id = ? ";
        try (PreparedStatement preparedStatement = connection.prepareStatement(queryBody)) {
            preparedStatement.setLong(1, guildID);
            ResultSet resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) {
                return resultSet.getInt(1);
            }
            return 0;
        } catch (SQLException ex) {
            throw new ChuuServiceException(ex);
        }
    }

    @Override
    public boolean getGuildConfigEmbed(Connection connection, long guildID) {
        @Language("MariaDB") String queryBody = "SELECT additional_embed FROM guild WHERE guild_id = ? ";
        try (PreparedStatement preparedStatement = connection.prepareStatement(queryBody)) {
            preparedStatement.setLong(1, guildID);
            ResultSet resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) {
                return resultSet.getBoolean(1);
            }
            return false;
        } catch (SQLException ex) {
            throw new ChuuServiceException(ex);
        }
    }

    @Override
    public List<LbEntry> getScrobblesLeaderboard(Connection connection, long guildId) {
        @Language("MariaDB") String queryBody = "SELECT b.lastfm_id,b.discord_id, sum(a.playnumber) AS ord " +
                "FROM scrobbled_artist a JOIN user b ON a.lastfm_id = b.lastfm_id " +
                "JOIN user_guild ug ON b.discord_id = ug.discord_id " +
                "WHERE ug.guild_id = ? " +
                "GROUP BY b.discord_id,b.lastfm_id " +
                "ORDER BY ord DESC ";
        return getLbEntries(connection, guildId, queryBody, ScrobbleLbEntry::new, false, -1);
    }

    @Override
    public List<CrownableArtist> getCrownable(Connection connection, Long discordId, Long guildId, boolean skipCrowns, boolean onlySecond, int crownDistance) {
        List<CrownableArtist> list = new ArrayList<>();
        String guildQuery;
        if (guildId != null) {
            guildQuery = """
                    SELECT *\s
                    FROM   (SELECT temp_2.name                                    AS name,\s
                                   temp_2.artist_id                               AS artist,\s
                                   temp_2.playnumber                              AS plays,\s
                                   Max(inn.playnumber)                            AS maxPlays,\s
                                   (SELECT Count(*)\s
                                    FROM   scrobbled_artist b\s
                                           JOIN user d\s
                                             ON b.lastfm_id = d.lastfm_id\s
                                           JOIN user_guild c\s
                                             ON d.discord_id = c.discord_id\s
                                    WHERE  c.guild_id = ?\s
                                           AND artist_id = temp_2.artist_id\s
                                           AND temp_2.playnumber <= b.playnumber) rank,\s
                                   Count(*)                                       AS total\s
                            FROM   (SELECT b.*\s
                                    FROM   scrobbled_artist b\s
                                           JOIN user d\s
                                             ON b.lastfm_id = d.lastfm_id\s
                                           JOIN user_guild c\s
                                             ON d.discord_id = c.discord_id\s
                                    WHERE  c.guild_id = ?) inn\s
                                   JOIN (SELECT artist_id,\s
                                                inn_c.name AS name,\s
                                                playnumber\s
                                         FROM   scrobbled_artist inn_a\s
                                                JOIN artist inn_c\s
                                                  ON inn_a.artist_id = inn_c.id\s
                                                JOIN user inn_b\s
                                                  ON inn_a.lastfm_id = inn_b.lastfm_id\s
                                                JOIN user_guild c\s
                                                  ON inn_b.discord_id = c.discord_id\s
                                         WHERE  c.guild_id = ?\s
                                                AND inn_b.discord_id = ?) temp_2\s
                                     ON temp_2.artist_id = inn.artist_id\s
                            GROUP  BY temp_2.artist_id\s
                            ORDER  BY temp_2.playnumber DESC) main\s
                    WHERE(? and rank = 2 or ((not ?) and
                                            ? and rank != 1 or\s
                                            not ? and not ?)) and (maxPlays - plays < ?) LIMIT  500\s""";
        } else {
            guildQuery = """
                    Select * from (SELECT temp_2.name as name, temp_2.artist_id as artist ,\s
                           temp_2.playnumber as plays,\s
                           Max(inn.playnumber) as maxPlays,\s
                           (SELECT Count(*)\s
                            FROM   scrobbled_artist b\s
                            WHERE  artist_id = temp_2.artist_id\s
                                   AND temp_2.playnumber <= b.playnumber) as rank, count(*) as total
                    FROM   scrobbled_artist inn\s
                           JOIN (SELECT artist_id,inn_c.name as name,
                                        playnumber\s
                                 FROM   scrobbled_artist inn_a\s
                                          join artist inn_c on inn_a.artist_id = inn_c.id                    \s
                                           JOIN user inn_b\s
                                          ON inn_a.lastfm_id = inn_b.lastfm_id\s
                                 WHERE  inn_b.discord_id = ?) temp_2\s
                             ON temp_2.artist_id = inn.artist_id\s
                    GROUP  BY temp_2.artist_id\s
                    ORDER  BY temp_2.playnumber DESC\s
                    ) main  where (? and rank = 2 or ((not ?) and
                                            ? and rank != 1 or\s
                                            not ? and not ?))  and (maxPlays - plays < ?)  limit 500;""";
        }


        int count = 0;
        int i = 1;
        try (PreparedStatement preparedStatement1 = connection.prepareStatement(guildQuery)) {
            if (guildId != null) {
                preparedStatement1.setLong(i++, guildId);
                preparedStatement1.setLong(i++, guildId);
                preparedStatement1.setLong(i++, guildId);
            }
            preparedStatement1.setLong(i++, discordId);
            preparedStatement1.setBoolean(i++, onlySecond);
            preparedStatement1.setBoolean(i++, onlySecond);
            preparedStatement1.setBoolean(i++, skipCrowns);
            preparedStatement1.setBoolean(i++, onlySecond);
            preparedStatement1.setBoolean(i++, skipCrowns);
            preparedStatement1.setInt(i, crownDistance);


            ResultSet resultSet1 = preparedStatement1.executeQuery();

            while (resultSet1.next()) {
                String artist = resultSet1.getString("name");

                int plays = resultSet1.getInt("plays");
                int rank = resultSet1.getInt("rank");
                int total = resultSet1.getInt("total");
                int maxPlays = resultSet1.getInt("maxPlays");

                CrownableArtist who = new CrownableArtist(artist, plays, maxPlays, rank, total);
                list.add(who);
            }

        } catch (SQLException e) {
            logger.warn(e.getMessage(), e);
            throw new ChuuServiceException(e);
        }
        return list;
    }

    @Override
    public Map<Long, Float> getRateLimited(Connection connection) {
        @Language("MariaDB") String queryString = "SELECT discord_id,queries_second  FROM rate_limited ";

        try (PreparedStatement preparedStatement = connection.prepareStatement(queryString)) {

            Map<Long, Float> returnList = new HashMap<>();
            /* Execute query. */
            ResultSet resultSet = preparedStatement.executeQuery();

            while (resultSet.next()) {

                long guildId = resultSet.getLong("discord_id");
                float queries_second = resultSet.getFloat("queries_second");

                returnList.put(guildId, queries_second);

            }
            return returnList;
        } catch (SQLException e) {
            throw new ChuuServiceException(e);
        }

    }

    @Override
    public WrapperReturnNowPlaying getGlobalWhoKnows(Connection con, long artistId, int limit, boolean includeBottedUsers, long ownerId) {

        @Language("MariaDB")
        String queryString =
                "SELECT a2.name, a.lastfm_id, a.playNumber, a2.url, c.discord_id,c.privacy_mode " +
                        "FROM  scrobbled_artist a " +
                        "JOIN artist a2 ON a.artist_id = a2.id  " +
                        "JOIN `user` c on c.lastFm_Id = a.lastFM_ID " +
                        " where   (? or not c.botted_account or c.discord_id = ? )  " +
                        "and  a2.id = ? " +
                        "ORDER BY a.playNumber desc ";
        queryString = limit == Integer.MAX_VALUE ? queryString : queryString + "limit " + limit;
        try (PreparedStatement preparedStatement = con.prepareStatement(queryString)) {

            int i = 1;
            preparedStatement.setBoolean(i++, includeBottedUsers);
            preparedStatement.setLong(i++, ownerId);
            preparedStatement.setLong(i, artistId);






            /* Execute query. */

            ResultSet resultSet = preparedStatement.executeQuery();
            String url = "";
            String artistName = "";
            List<ReturnNowPlaying> returnList = new ArrayList<>();



            /* Get results. */
            int j = 0;
            while (resultSet.next() && (j < limit)) {
                url = resultSet.getString("a2.url");
                artistName = resultSet.getString("a2.name");
                String lastfmId = resultSet.getString("a.lastFM_ID");

                int playNumber = resultSet.getInt("a.playNumber");
                long discordId = resultSet.getLong("c.discord_ID");
                PrivacyMode privacyMode = PrivacyMode.valueOf(resultSet.getString("c.privacy_mode"));

                returnList.add(new GlobalReturnNowPlaying(discordId, lastfmId, artistName, playNumber, privacyMode));
            }
            /* Return booking. */
            return new WrapperReturnNowPlaying(returnList, returnList.size(), url, artistName);

        } catch (SQLException e) {
            throw new ChuuServiceException(e);
        }
    }


    @Override
    public List<ScrobbledArtist> getRecommendations(Connection connection, long giverDiscordId,
                                                    long receiverDiscordId, boolean doPast, int limit) {
        @Language("MariaDB") String queryBody = """
                SELECT\s
                b.name,b.url,b.id,a.playnumber\s
                FROM scrobbled_artist a\s
                JOIN artist b ON a.artist_id = b.id\s
                JOIN user c ON a.lastfm_id = c.lastfm_id WHERE c.discord_id =  ?\s
                 AND  (?  OR (a.artist_id NOT IN (SELECT r.artist_id FROM past_recommendations r WHERE receiver_id =  ? ))) AND a.artist_id NOT IN (SELECT in_b.artist_id FROM scrobbled_artist in_b  JOIN user in_c ON in_b.lastfm_id = in_c.lastfm_id WHERE in_c.discord_id = ? ) ORDER BY a.playnumber DESC LIMIT ?""";

        List<ScrobbledArtist> scrobbledArtists = new ArrayList<>();
        try (PreparedStatement preparedStatement = connection.prepareStatement(queryBody)) {
            int i = 1;
            preparedStatement.setLong(i++, giverDiscordId);
            preparedStatement.setBoolean(i++, doPast);
            preparedStatement.setLong(i++, receiverDiscordId);

            preparedStatement.setLong(i++, receiverDiscordId);
            preparedStatement.setInt(i, limit);

            ResultSet resultSet = preparedStatement.executeQuery();

            while (resultSet.next()) {
                String name = resultSet.getString("name");
                String url = resultSet.getString("url");
                long id = resultSet.getLong("id");
                int playnumber = resultSet.getInt("playnumber");

                ScrobbledArtist scrobbledArtist = new ScrobbledArtist(name, playnumber, url);
                scrobbledArtist.setArtistId(id);
                scrobbledArtists.add(scrobbledArtist);
            }
            return scrobbledArtists;
        } catch (SQLException e) {
            logger.warn(e.getMessage(), e);
            throw new ChuuServiceException(e);
        }
    }


    @Override
    public UniqueWrapper<ArtistPlays> getUniqueArtist(Connection connection, Long guildID, String lastfmId) {
        @Language("MariaDB") String queryString = "SELECT * " +
                "FROM(  " +
                "       SELECT a2.name, playnumber, a.lastfm_id ,b.discord_id" +
                "       FROM scrobbled_artist a JOIN user b " +
                "       ON a.lastfm_id = b.lastfm_id " +
                "       JOIN user_guild c ON b.discord_id = c.discord_id " +
                " JOIN artist a2 ON a.artist_id = a2.id " +
                "       WHERE c.guild_id = ? AND a.playnumber > 2 " +
                "       GROUP BY a.artist_id " +
                "       HAVING count( *) = 1) temp " +
                "WHERE temp.lastfm_id = ? AND temp.playnumber > 1 " +
                " ORDER BY temp.playnumber DESC ";

        try (PreparedStatement preparedStatement = connection.prepareStatement(queryString)) {
            int i = 1;
            preparedStatement.setLong(i++, guildID);
            preparedStatement.setString(i, lastfmId);
            ResultSet resultSet = preparedStatement.executeQuery();

            List<ArtistPlays> returnList = new ArrayList<>();
            long discordId = 0;
            while (resultSet.next()) {
                discordId = resultSet.getLong("temp.discord_id");
                String name = resultSet.getString("temp.name");
                int countA = resultSet.getInt("temp.playNumber");

                returnList.add(new ArtistPlays(name, countA));

            }
            return new UniqueWrapper<>(returnList.size(), discordId, lastfmId, returnList);


        } catch (SQLException e) {
            logger.warn(e.getMessage(), e);
            throw new ChuuServiceException(e);
        }
    }


    @Override
    public ResultWrapper<UserArtistComparison> similar(Connection connection, List<String> lastfMNames, int limit) {
        String userA = lastfMNames.get(0);
        String userB = lastfMNames.get(1);

        @Language("MariaDB") String queryString =
                "SELECT c.name  , a.playnumber,b.playnumber ," +
                        "((a.playnumber + b.playnumber)/(abs(a.playnumber-b.playnumber)+1))  *" +
                        " (((a.playnumber + b.playnumber)) * 2.5) * " +
                        " IF((a.playnumber > 10 * b.playnumber OR b.playnumber > 10 * a.playnumber) AND LEAST(a.playnumber,b.playnumber) < 400 ,0.01,2) " +
                        "media ," +
                        " c.url " +
                        "FROM " +
                        "(SELECT artist_id,playnumber " +
                        "FROM scrobbled_artist " +
                        "JOIN user b ON scrobbled_artist.lastfm_id = b.lastfm_id " +
                        "WHERE b.lastfm_id = ? ) a " +
                        "JOIN " +
                        "(SELECT artist_id,playnumber " +
                        "FROM scrobbled_artist " +
                        " JOIN user b ON scrobbled_artist.lastfm_id = b.lastfm_id " +
                        " WHERE b.lastfm_id = ? ) b " +
                        "ON a.artist_id=b.artist_id " +
                        "JOIN artist c " +
                        "ON c.id=b.artist_id" +
                        " ORDER BY media DESC";

        try (PreparedStatement preparedStatement = connection.prepareStatement(queryString)) {

            int i = 1;
            preparedStatement.setString(i++, userA);
            preparedStatement.setString(i, userB);


            /* Execute query. */
            ResultSet resultSet = preparedStatement.executeQuery();
            List<UserArtistComparison> returnList = new ArrayList<>();

            int counter = 0;
            while (resultSet.next()) {
                counter++;
                if (counter <= limit) {
                    String name = resultSet.getString("c.name");
                    int countA = resultSet.getInt("a.playNumber");
                    int countB = resultSet.getInt("b.playNumber");
                    String url = resultSet.getString("c.url");
                    returnList.add(new UserArtistComparison(countA, countB, name, userA, userB, url));
                }
            }

            return new ResultWrapper<>(counter, returnList);

        } catch (SQLException e) {
            throw new ChuuServiceException(e);
        }

    }

    @Override
    public WrapperReturnNowPlaying knows(Connection con, long artistId, long guildId, int limit) {

        @Language("MariaDB")
        String queryString =
                "SELECT a2.name, a.lastfm_id, a.playNumber, a2.url, c.discord_id " +
                        "FROM  scrobbled_artist a" +
                        " JOIN artist a2 ON a.artist_id = a2.id  " +
                        "JOIN `user` c on c.lastFm_Id = a.lastFM_ID " +
                        "JOIN user_guild d on c.discord_ID = d.discord_Id " +
                        "where d.guild_Id = ? " +
                        "and  a2.id = ? " +
                        "ORDER BY a.playNumber desc ";
        queryString = limit == Integer.MAX_VALUE ? queryString : queryString + "limit " + limit;
        try (PreparedStatement preparedStatement = con.prepareStatement(queryString)) {

            int i = 1;
            preparedStatement.setLong(i++, guildId);
            preparedStatement.setLong(i, artistId);



            /* Execute query. */

            ResultSet resultSet = preparedStatement.executeQuery();
            String url = "";
            String artistName = "";
            List<ReturnNowPlaying> returnList = new ArrayList<>();



            /* Get results. */
            int j = 0;
            while (resultSet.next() && (j < limit)) {
                url = resultSet.getString("a2.url");
                artistName = resultSet.getString("a2.name");
                String lastfmId = resultSet.getString("a.lastFM_ID");

                int playNumber = resultSet.getInt("a.playNumber");
                long discordId = resultSet.getLong("c.discord_ID");

                returnList.add(new ReturnNowPlaying(discordId, lastfmId, artistName, playNumber));
            }
            /* Return booking. */
            return new WrapperReturnNowPlaying(returnList, returnList.size(), url, artistName);

        } catch (SQLException e) {
            throw new ChuuServiceException(e);
        }
    }

    @Override
    public UniqueWrapper<ArtistPlays> getCrowns(Connection connection, String lastfmId, long guildID,
                                                int crownThreshold) {
        List<ArtistPlays> returnList = new ArrayList<>();
        long discordId = 0;

        @Language("MariaDB") String queryString = "SELECT a2.name, b.discord_id , playnumber AS orden" +
                " FROM  scrobbled_artist  a" +
                " JOIN user b ON a.lastfm_id = b.lastfm_id " +
                " JOIN artist a2 ON a.artist_id = a2.id " +
                " WHERE  b.lastfm_id = ?" +
                " AND playnumber >= ? " +
                " AND  playnumber >= ALL" +
                "       (SELECT max(b.playnumber) " +
                " FROM " +
                "(SELECT in_a.artist_id,in_a.playnumber" +
                " FROM scrobbled_artist in_a  " +
                " JOIN " +
                " user in_b" +
                " ON in_a.lastfm_id = in_b.lastfm_id" +
                " NATURAL JOIN " +
                " user_guild in_c" +
                " WHERE guild_id = ?" +
                "   ) AS b" +
                " WHERE b.artist_id = a.artist_id" +
                " GROUP BY artist_id)" +
                " ORDER BY orden DESC";

        try (PreparedStatement preparedStatement = connection.prepareStatement(queryString)) {
            int i = 1;
            preparedStatement.setString(i++, lastfmId);
            preparedStatement.setInt(i++, crownThreshold);
            preparedStatement.setLong(i, guildID);

            ResultSet resultSet = preparedStatement.executeQuery();


            while (resultSet.next()) {
                discordId = resultSet.getLong("b.discord_id");

                String artist = resultSet.getString("a2.name");
                int plays = resultSet.getInt("orden");
                returnList.add(new ArtistPlays(artist, plays));
            }
        } catch (SQLException e) {
            logger.warn(e.getMessage(), e);
            throw new ChuuServiceException(e);
        }
        return new UniqueWrapper<>(returnList.size(), discordId, lastfmId, returnList);
    }

    @Override
    public ResultWrapper<ScrobbledArtist> getGuildTop(Connection connection, Long guildID, int limit,
                                                      boolean doCount) {
        @Language("MariaDB") String normalQUery = "SELECT d.name, sum(playnumber) AS orden ,url  ";

        String countQuery = "Select count(*) as orden ";


        String queryBody = "FROM  scrobbled_artist a" +
                " JOIN user b" +
                " ON a.lastfm_id = b.lastfm_id" +
                " JOIN artist d " +
                " ON a.artist_id = d.id";
        if (guildID != null) {
            queryBody += " JOIN  user_guild c" +
                    " ON b.discord_id=c.discord_id" +
                    " WHERE c.guild_id = ?";
        }

        List<ScrobbledArtist> list = new ArrayList<>();
        int count = 0;
        int i = 1;
        try (PreparedStatement preparedStatement1 = connection.prepareStatement(normalQUery + queryBody + " GROUP BY artist_id  ORDER BY orden DESC  limit ?")) {
            if (guildID != null)
                preparedStatement1.setLong(i++, guildID);

            preparedStatement1.setInt(i, limit);

            ResultSet resultSet1 = preparedStatement1.executeQuery();

            while (resultSet1.next()) {
                String artist = resultSet1.getString("d.name");
                String url = resultSet1.getString("url");

                int plays = resultSet1.getInt("orden");
                ScrobbledArtist who = new ScrobbledArtist(artist, plays, url);
                list.add(who);
            }
            if (doCount) {

                PreparedStatement preparedStatement = connection.prepareStatement(countQuery + queryBody);
                i = 1;
                if (guildID != null)
                    preparedStatement.setLong(i, guildID);

                ResultSet resultSet = preparedStatement.executeQuery();
                if (resultSet.next()) {
                    count = resultSet.getInt(1);
                }


            }
        } catch (SQLException e) {
            logger.warn(e.getMessage(), e);
            throw new ChuuServiceException(e);
        }
        return new ResultWrapper<>(count, list);
    }

    @Override
    public int userPlays(Connection con, long artistId, String whom) {
        @Language("MariaDB") String queryString = "SELECT a.playnumber " +
                "FROM scrobbled_artist a JOIN user b ON a.lastfm_id=b.lastfm_id " +
                "JOIN artist c ON a.artist_id = c.id " +
                "WHERE a.lastfm_id = ? AND c.id = ?";
        try (PreparedStatement preparedStatement = con.prepareStatement(queryString)) {
            int i = 1;
            preparedStatement.setString(i++, whom);
            preparedStatement.setLong(i, artistId);




            /* Execute query. */
            ResultSet resultSet = preparedStatement.executeQuery();
            if (!resultSet.next())
                return 0;
            return resultSet.getInt("a.playNumber");


        } catch (SQLException e) {
            throw new ChuuServiceException(e);
        }
    }

    @Override
    public List<LbEntry> crownsLeaderboard(Connection connection, long guildID, int threshold) {
        @Language("MariaDB") String queryString = "SELECT t2.lastfm_id,t3.discord_id,count(t2.lastfm_id) ord " +
                "FROM " +
                "( " +
                "SELECT " +
                "        a.artist_id,max(a.playnumber) plays " +
                "    FROM " +
                "         scrobbled_artist a  " +
                "    JOIN " +
                "        user b  " +
                "            ON a.lastfm_id = b.lastfm_id  " +
                "    JOIN " +
                "        user_guild c  " +
                "            ON b.discord_id = c.discord_id  " +
                "    WHERE " +
                "        c.guild_id = ?  " +
                "    GROUP BY " +
                "        a.artist_id  " +
                "  ) t " +
                "  JOIN scrobbled_artist t2  " +
                "   " +
                "  ON t.plays = t2.playnumber AND t.artist_id = t2.artist_id " +
                "  JOIN user t3  ON t2.lastfm_id = t3.lastfm_id  " +
                "    JOIN " +
                "        user_guild t4  " +
                "            ON t3.discord_id = t4.discord_id  " +
                "    WHERE " +
                "        t4.guild_id = ?  ";
        if (threshold != 0) {
            queryString += " and t2.playnumber >= ? ";
        }


        queryString += "  GROUP BY t2.lastfm_id,t3.discord_id " +
                "  ORDER BY ord DESC";

        return getLbEntries(connection, guildID, queryString, CrownsLbEntry::new, true, threshold);


    }

    @Override
    public List<LbEntry> uniqueLeaderboard(Connection connection, long guildId) {
        @Language("MariaDB") String queryString = "SELECT  " +
                "    count(temp.lastfm_id) AS ord,temp.lastfm_id,temp.discord_id " +
                "FROM " +
                "    (SELECT  " +
                "         a.lastfm_id, b.discord_id " +
                "    FROM " +
                "        scrobbled_artist a " +
                "    JOIN user b ON a.lastfm_id = b.lastfm_id " +
                "    JOIN user_guild c ON b.discord_id = c.discord_id " +
                "    WHERE " +
                "        c.guild_id = ? " +
                "            AND a.playnumber > 2 " +
                "    GROUP BY a.artist_id " +
                "    HAVING COUNT(*) = 1) temp " +
                "GROUP BY lastfm_id " +
                "ORDER BY ord DESC";

        return getLbEntries(connection, guildId, queryString, UniqueLbEntry::new, false, 0);
    }


    @Override
    public int userArtistCount(Connection con, String whom, int threshold) {
        @Language("MariaDB") String queryString = "SELECT count(*) AS numb FROM scrobbled_artist WHERE scrobbled_artist.lastfm_id= ? and playNumber >= ?";
        try (PreparedStatement preparedStatement = con.prepareStatement(queryString)) {
            int i = 1;
            preparedStatement.setString(i++, whom);
            preparedStatement.setInt(i, threshold);


            /* Execute query. */
            ResultSet resultSet = preparedStatement.executeQuery();
            if (!resultSet.next())
                return 0;
            return resultSet.getInt("numb");


        } catch (SQLException e) {
            throw new ChuuServiceException(e);
        }
    }

    @Override
    public List<LbEntry> artistLeaderboard(Connection con, long guildID, int threshold) {
        @Language("MariaDB") String queryString = "(SELECT  " +
                "        a.lastfm_id , count(*) AS ord, c.discord_id" +
                "    FROM " +
                "        scrobbled_artist a " +
                "    JOIN user b ON a.lastfm_id = b.lastfm_id " +
                "    JOIN user_guild c ON b.discord_id = c.discord_id " +
                "    WHERE " +
                "        c.guild_id = ? and " +
                "       c.guild_id = ? and " +
                "       a.playnumber >= ?" +
                " GROUP BY a.lastfm_id,c.discord_id " +
                "    ORDER BY ord DESC    )";

        return getLbEntries(con, guildID, queryString, ArtistLbEntry::new, true, threshold);
    }

    @Override
    public List<LbEntry> obscurityLeaderboard(Connection connection, long guildId) {
        //OBtains total plays, and other users plays on your artist
        // Obtains uniques, percentage of uniques, and plays on uniques
        //"\t#full artist table, we will filter later because is somehow faster :D\n" +
        @Language("MariaDB") String queryString = """

                SELECT finalmain.lastfm_id,  POW(((mytotalplays / (other_plays_on_my_artists)) * (as_unique_coefficient + 1)),
                            0.4) AS ord , c.discord_id
                FROM (
                SELECT\s
                    main.lastfm_id,
                    (SELECT\s
                              COALESCE(SUM(a.playnumber) * (COUNT(*)), 0)
                        FROM
                            scrobbled_artist a
                        WHERE
                            lastfm_id = main.lastfm_id) AS mytotalplays,
                    (SELECT\s
                             COALESCE(SUM(a.playnumber), 1)
                        FROM
                            scrobbled_artist a
                        WHERE
                            lastfm_id != main.lastfm_id
                                AND a.artist_id IN (SELECT\s
                                    artist_id
                                FROM
                                    artist
                                WHERE
                                    lastfm_id = main.lastfm_id))AS  other_plays_on_my_artists,
                    (SELECT\s
                            COUNT(*) / (SELECT\s
                                        COUNT(*) + 1
                                    FROM
                                        scrobbled_artist a
                                    WHERE
                                        lastfm_id = main.lastfm_id) * (COALESCE(SUM(playnumber), 1))
                        FROM
                            (SELECT\s
                                artist_id, playnumber, a.lastfm_id
                            FROM
                                scrobbled_artist a
                            GROUP BY a.artist_id
                            HAVING COUNT(*) = 1) temp\s
                        WHERE
                            temp.lastfm_id = main.lastfm_id
                                AND temp.playnumber > 1
                        ) as_unique_coefficient
                FROM
                    scrobbled_artist main
                   \s
                GROUP BY main.lastfm_id
                ) finalmain JOIN user b
                ON finalmain.lastfm_id = b.lastfm_id\s
                JOIN user_guild c ON b.discord_id = c.discord_id\s
                WHERE c.guild_id = ? ORDER BY ord DESC""";

        return getLbEntries(connection, guildId, queryString, ObscurityEntry::new, false, 0);
    }

    @Override
    public PresenceInfo getRandomArtistWithUrl(Connection connection) {

        @Language("MariaDB") String queryString =
                """
                        SELECT\s
                            b.name,
                            b.url,
                             discord_id,
                            (SELECT\s
                                    SUM(playnumber)
                                FROM
                                    scrobbled_artist
                                WHERE
                                    artist_id = a.artist_id) AS summa
                        FROM
                            scrobbled_artist a
                                JOIN
                            artist b ON a.artist_id = b.id
                                NATURAL JOIN
                            user c
                        WHERE
                            b.id IN (SELECT\s
                                    rando.id
                                FROM
                                    (SELECT\s
                                        a.id
                                    FROM
                                        artist a
                                        WHERE a.url IS NOT NULL
                                        AND a.url != ''
                                    ORDER BY RAND()
                                    LIMIT 1) rando)
                        ORDER BY RAND()
                        LIMIT 1;""";
        try (PreparedStatement preparedStatement = connection.prepareStatement(queryString)) {

            ResultSet resultSet = preparedStatement.executeQuery();
            if (!resultSet.next())
                return null;

            String artistName = resultSet.getString("name");
            String url = resultSet.getString("url");

            long summa = resultSet.getLong("summa");
            long discordId = resultSet.getLong("discord_id");
            return new PresenceInfo(artistName, url, summa, discordId);

        } catch (SQLException e) {
            throw new ChuuServiceException(e);
        }
    }

    @Override
    public StolenCrownWrapper artistsBehind(Connection connection, String ogUser, String queriedUser,
                                            int threshold) {
        List<StolenCrown> returnList = new ArrayList<>();
        long discordid = 0;
        long discordid2 = 0;
        @Language("MariaDB") String queryString = """
                SELECT\s
                    inn.name AS artist ,inn.orden AS ogplays , inn.discord_id AS ogid , inn2.discord_id queriedid,  inn2.orden AS queriedplays
                FROM
                    (SELECT\s
                        a.artist_id, a2.name, b.discord_id, playnumber AS orden
                    FROM
                        scrobbled_artist a
                    JOIN user b ON a.lastfm_id = b.lastfm_id
                 JOIN artist a2 ON a.artist_id = a2.id     WHERE
                        a.lastfm_id = ?) inn
                 join (SELECT\s
                        a.artist_id, a2.name, b.discord_id, playnumber AS orden
                    FROM
                        scrobbled_artist a
                    JOIN user b ON a.lastfm_id = b.lastfm_id
                 JOIN artist a2 ON a.artist_id = a2.id     WHERE
                        a.lastfm_id = ?) inn2   on inn2.artist_id = inn.artist_id and inn2.orden >= inn.orden\s""";


        if (threshold != 0) {
            queryString += " and inn.orden >= ? && inn2.orden >= ? ";
        }


        queryString += " ORDER BY inn.orden DESC , inn2.orden DESC\n";

        try (PreparedStatement preparedStatement = connection.prepareStatement(queryString)) {
            int i = 1;
            preparedStatement.setString(i++, ogUser);
            preparedStatement.setString(i++, queriedUser);

            if (threshold != 0) {
                preparedStatement.setInt(i++, threshold);
                preparedStatement.setInt(i, threshold);
            }
            ResultSet resultSet = preparedStatement.executeQuery();


            while (resultSet.next()) {
                discordid = resultSet.getLong("ogId");
                discordid2 = resultSet.getLong("queriedId");
                String artist = resultSet.getString("artist");
                int plays = resultSet.getInt("ogPlays");
                int plays2 = resultSet.getInt("queriedPlays");

                returnList.add(new StolenCrown(artist, plays, plays2));
            }
        } catch (SQLException e) {
            logger.warn(e.getMessage(), e);
            throw new ChuuServiceException(e);
        }
        //Ids will be 0 if returnlist is empty;
        return new StolenCrownWrapper(discordid, discordid2, returnList);
    }

    @Override
    public StolenCrownWrapper getCrownsStolenBy(Connection connection, String ogUser, String queriedUser,
                                                long guildId, int threshold) {
        List<StolenCrown> returnList = new ArrayList<>();
        long discordid = 0;
        long discordid2 = 0;
        @Language("MariaDB") String queryString = """
                SELECT\s
                    inn.name AS artist ,inn.orden AS ogplays , inn.discord_id AS ogid , inn2.discord_id queriedid,  inn2.orden AS queriedplays
                FROM
                    (SELECT\s
                        a.artist_id, a2.name, b.discord_id, playnumber AS orden
                    FROM
                        scrobbled_artist a
                    JOIN user b ON a.lastfm_id = b.lastfm_id
                 JOIN artist a2 ON a.artist_id = a2.id     WHERE
                        a.lastfm_id = ?) inn
                        JOIN
                    (SELECT\s
                        artist_id, b.discord_id, playnumber AS orden
                    FROM
                        scrobbled_artist a
                    JOIN user b ON a.lastfm_id = b.lastfm_id
                    WHERE
                        b.lastfm_id = ?) inn2 ON inn.artist_id = inn2.artist_id
                WHERE
                    (inn2.artist_id , inn2.orden) = (SELECT\s
                            in_a.artist_id, MAX(in_a.playnumber)
                        FROM
                            scrobbled_artist in_a
                                JOIN
                            user in_b ON in_a.lastfm_id = in_b.lastfm_id
                                NATURAL JOIN
                            user_guild in_c
                        WHERE
                            guild_id = ?
                                AND artist_id = inn2.artist_id)
                        AND (inn.artist_id , inn.orden) = (SELECT\s
                            in_a.artist_id, in_a.playnumber
                        FROM
                            scrobbled_artist in_a
                                JOIN
                            user in_b ON in_a.lastfm_id = in_b.lastfm_id
                                NATURAL JOIN
                            user_guild in_c
                        WHERE
                            guild_id = ?
                                AND artist_id = inn.artist_id
                        ORDER BY in_a.playnumber DESC
                        LIMIT 1 , 1)
                """;

        if (threshold != 0) {
            queryString += " and inn.orden >= ? && inn2.orden >= ? ";
        }


        queryString += " ORDER BY inn.orden DESC , inn2.orden DESC\n";

        try (PreparedStatement preparedStatement = connection.prepareStatement(queryString)) {
            int i = 1;
            preparedStatement.setString(i++, ogUser);
            preparedStatement.setString(i++, queriedUser);

            preparedStatement.setLong(i++, guildId);
            preparedStatement.setLong(i++, guildId);
            if (threshold != 0) {
                preparedStatement.setInt(i++, threshold);
                preparedStatement.setInt(i, threshold);
            }
            ResultSet resultSet = preparedStatement.executeQuery();


            while (resultSet.next()) {
                discordid = resultSet.getLong("ogId");
                discordid2 = resultSet.getLong("queriedId");
                String artist = resultSet.getString("artist");
                int plays = resultSet.getInt("ogPlays");
                int plays2 = resultSet.getInt("queriedPlays");

                returnList.add(new StolenCrown(artist, plays, plays2));
            }
        } catch (SQLException e) {
            logger.warn(e.getMessage(), e);
            throw new ChuuServiceException(e);
        }
        //Ids will be 0 if returnlist is empty;
        return new StolenCrownWrapper(discordid, discordid2, returnList);
    }

    @Override
    public UniqueWrapper<AlbumPlays> getUserAlbumCrowns(Connection connection, String lastfmId, int crownThreshold, long guildID) {
        long discordId = -1L;
        @Language("MariaDB") String queryString = "SELECT d.name, a2.album_name, b.discord_id , playnumber AS orden" +
                " FROM  scrobbled_album  a" +
                " JOIN user b ON a.lastfm_id = b.lastfm_id " +
                " JOIN album a2 ON a.album_id = a2.id " +
                " join artist d on a2.artist_id = d.id " +
                " WHERE  b.lastfm_id = ?" +
                " AND playnumber >= ? " +
                " AND  playnumber >= ALL" +
                "       (SELECT max(b.playnumber) " +
                " FROM " +
                "(SELECT in_a.album_id,in_a.playnumber" +
                " FROM scrobbled_album in_a  " +
                " JOIN " +
                " user in_b" +
                " ON in_a.lastfm_id = in_b.lastfm_id" +
                " NATURAL JOIN " +
                " user_guild in_c" +
                " WHERE guild_id = ?" +
                "   ) AS b" +
                "" +
                " WHERE b.album_id = a.album_id" +
                " GROUP BY album_id)" +
                " ORDER BY orden DESC";

        List<AlbumPlays> returnList = new ArrayList<>();
        try (PreparedStatement preparedStatement = connection.prepareStatement(queryString)) {
            int i = 1;
            preparedStatement.setString(i++, lastfmId);
            preparedStatement.setInt(i++, crownThreshold);
            preparedStatement.setLong(i, guildID);

            ResultSet resultSet = preparedStatement.executeQuery();


            while (resultSet.next()) {
                discordId = resultSet.getLong("b.discord_id");

                String artist = resultSet.getString("d.name");
                String album = resultSet.getString("a2.album_name");


                int plays = resultSet.getInt("orden");
                returnList.add(new AlbumPlays(artist, plays, album));
            }
        } catch (SQLException e) {
            logger.warn(e.getMessage(), e);
            throw new ChuuServiceException(e);
        }
        return new UniqueWrapper<>(returnList.size(), discordId, lastfmId, returnList);
    }


    @Override
    public List<LbEntry> albumCrownsLeaderboard(Connection con, long guildID, int threshold) {
        @Language("MariaDB") String queryString = "SELECT t2.lastfm_id,t3.discord_id,count(t2.lastfm_id) ord " +
                "FROM " +
                "( " +
                "SELECT " +
                "        a.album_id,max(a.playnumber) plays " +
                "    FROM " +
                "         scrobbled_album a  " +
                "    JOIN " +
                "        user b  " +
                "            ON a.lastfm_id = b.lastfm_id  " +
                "    JOIN " +
                "        user_guild c  " +
                "            ON b.discord_id = c.discord_id  " +
                "    WHERE " +
                "        c.guild_id = ?  " +
                "    GROUP BY " +
                "        a.album_id  " +
                "  ) t " +
                "  JOIN scrobbled_album t2  " +
                "   " +
                "  ON t.plays = t2.playnumber AND t.album_id = t2.album_id " +
                "  JOIN user t3  ON t2.lastfm_id = t3.lastfm_id  " +
                "    JOIN " +
                "        user_guild t4  " +
                "            ON t3.discord_id = t4.discord_id  " +
                "    WHERE " +
                "        t4.guild_id = ?  ";
        if (threshold != 0) {
            queryString += " and t2.playnumber > ? ";
        }


        queryString += "  GROUP BY t2.lastfm_id,t3.discord_id " +
                "  ORDER BY ord DESC";


        return getLbEntries(con, guildID, queryString, AlbumCrownLbEntry::new, true, threshold);
    }

    @Override
    public ObscuritySummary getUserObscuritPoints(Connection connection, String lastfmId) {
        @Language("MariaDB") String queryString = """
                \tSELECT  b, other_plays_on_my_artists, unique_coefficient,
                \tPOW(((b/ (other_plays_on_my_artists)) * (unique_coefficient + 1)),0.4) AS total
                \t\tFROM (

                \tSELECT (SELECT sum(a.playnumber) * count(*) FROM\s
                \tscrobbled_artist a\s
                \tWHERE lastfm_id = main.lastfm_id) AS b , \s
                \t\t   (SELECT COALESCE(Sum(a.playnumber), 1)\s
                \t\t\tFROM   scrobbled_artist a\s
                 WHERE  lastfm_id != main.lastfm_id\s
                   AND a.artist_id IN (SELECT artist_id\s
                   FROM   artist\s
                   WHERE  lastfm_id = main.lastfm_id)) AS\s
                   other_plays_on_my_artists,\s
                   (SELECT Count(*) / (SELECT Count(*) + 1\s
                   FROM   scrobbled_artist a\s
                \t\t\t\t\t\t\t   WHERE  lastfm_id = main.lastfm_id) * (\s
                \t\t\t\t   COALESCE(Sum(playnumber\s
                \t\t\t\t\t\t\t), 1) )\s
                \t\t\tFROM   (SELECT artist_id,\s
                \t\t\t\t\t\t   playnumber,\s
                \t\t\t\t\t\t   a.lastfm_id\s
                \t\t\t\t\tFROM   scrobbled_artist a\s
                \t\t\t\t\tGROUP  BY a.artist_id\s
                \t\t\t\t\tHAVING Count(*) = 1) temp\s
                \t\t\tWHERE  temp.lastfm_id = main.lastfm_id\s
                \t\t\t\t   AND temp.playnumber > 1)\s
                \t\t   AS unique_coefficient                     \s
                \tFROM   scrobbled_artist main\s
                \tWHERE  lastfm_id =  ? GROUP BY lastfm_id
                \t
                \t) outer_main
                """;
        try (PreparedStatement preparedStatement = connection.prepareStatement(queryString)) {
            int i = 1;
            preparedStatement.setString(i, lastfmId);
            ResultSet resultSet = preparedStatement.executeQuery();

            if (!resultSet.next()) {
                return null;

            }

            int totalPlays = resultSet.getInt("b");
            int otherPlaysOnMyArtists = resultSet.getInt("other_plays_on_my_artists");
            int uniqueCoefficient = resultSet.getInt("unique_coefficient");
            int total = resultSet.getInt("total");

            return new ObscuritySummary(totalPlays, otherPlaysOnMyArtists, uniqueCoefficient, total);


        } catch (SQLException e) {
            logger.warn(e.getMessage(), e);
        }
        return null;
    }

    @Override
    public int getRandomCount(Connection connection, Long userId) {
        @Language("MariaDB") String queryString = """
                SELECT\s
                  count(*) as counted FROM
                    randomlinks\s
                """;
        if (userId != null) {
            queryString += "WHERE discord_id = ?";

        }
        try (PreparedStatement preparedStatement = connection.prepareStatement(queryString)) {
            if (userId != null) {
                preparedStatement.setLong(1, userId);
            }
            ResultSet resultSet = preparedStatement.executeQuery();
            if (!resultSet.next()) {
                return 0;
            }

            return resultSet.getInt("counted");

        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
            throw new ChuuServiceException(e);
        }
    }

    @Override
    public List<GlobalCrown> getGlobalKnows(Connection connection, long artistID, boolean includeBottedUsers, long ownerId) {
        List<GlobalCrown> returnedList = new ArrayList<>();
        @Language("MariaDB") String queryString = """
                SELECT  playnumber AS ord, discord_id, l.lastfm_id, l.botted_account
                 FROM  scrobbled_artist ar
                  	 	 JOIN user l ON ar.lastfm_id = l.lastfm_id         WHERE  ar.artist_id = ?    and   (? or not l.botted_account or l.discord_id = ? )            ORDER BY  playnumber DESC""";


        try (PreparedStatement preparedStatement = connection.prepareStatement(queryString)) {
            int i = 1;
            preparedStatement.setLong(i++, artistID);
            preparedStatement.setBoolean(i++, includeBottedUsers);
            preparedStatement.setLong(i, ownerId);


            ResultSet resultSet = preparedStatement.executeQuery();
            int j = 1;
            while (resultSet.next()) {

                String lastfmId = resultSet.getString("lastfm_id");
                long discordId = resultSet.getLong("discord_id");
                int crowns = resultSet.getInt("ord");
                boolean bootedAccount = resultSet.getBoolean("botted_account");

                returnedList.add(new GlobalCrown(lastfmId, discordId, crowns, j++, bootedAccount));
            }
            return returnedList;
        } catch (SQLException e) {
            logger.warn(e.getMessage(), e);
            throw new ChuuServiceException((e));
        }
    }

    //TriFunction is not the simplest approach but i felt like using it so :D
    @NotNull
    private List<LbEntry> getLbEntries(Connection connection, long guildId, String
            queryString, TriFunction<String, Long, Integer, LbEntry> fun, boolean needsReSet, int resetThreshold) {
        List<LbEntry> returnedList = new ArrayList<>();
        try (PreparedStatement preparedStatement = connection.prepareStatement(queryString)) {
            int i = 1;
            preparedStatement.setLong(i, guildId);
            if (needsReSet) {
                preparedStatement.setLong(++i, guildId);
                if (resetThreshold != 0)
                    preparedStatement.setLong(++i, resetThreshold);
            }
            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) { //&& (j < 10 && j < rows)) {
                String lastfmId = resultSet.getString("lastfm_id");
                long discordId = resultSet.getLong("discord_id");
                int crowns = resultSet.getInt("ord");

                returnedList.add(fun.apply(lastfmId, discordId, crowns));


            }
            return returnedList;
        } catch (SQLException e) {
            logger.warn(e.getMessage(), e);
            throw new ChuuServiceException((e));
        }
    }


    @Override
    public WrapperReturnNowPlaying whoKnowsAlbum(Connection con, long albumId, long guildId, int limit) {

        @Language("MariaDB")
        String queryString =
                "SELECT a2.album_name,a3.name, a.lastfm_id, a.playNumber, a2.url, c.discord_id " +
                        "FROM  scrobbled_album a" +
                        " JOIN album a2 ON a.album_id = a2.id  " +
                        " join artist a3 on a2.artist_id = a3.id " +
                        "JOIN `user` c on c.lastFm_Id = a.lastFM_ID " +
                        "JOIN user_guild d on c.discord_ID = d.discord_Id " +
                        "where d.guild_Id = ? " +
                        "and  a2.id = ? " +
                        "ORDER BY a.playNumber desc ";
        queryString = limit == Integer.MAX_VALUE ? queryString : queryString + "limit " + limit;
        try (PreparedStatement preparedStatement = con.prepareStatement(queryString)) {

            int i = 1;
            preparedStatement.setLong(i++, guildId);
            preparedStatement.setLong(i, albumId);



            /* Execute query. */

            ResultSet resultSet = preparedStatement.executeQuery();
            String url = "";
            String artistName = "";
            String albumName;
            List<ReturnNowPlaying> returnList = new ArrayList<>();



            /* Get results. */
            int j = 0;
            while (resultSet.next() && (j < limit)) {
                url = resultSet.getString("a2.url");
                artistName = resultSet.getString("a3.name");
                albumName = resultSet.getString("a2.album_name");

                String lastfmId = resultSet.getString("a.lastFM_ID");

                int playNumber = resultSet.getInt("a.playNumber");
                long discordId = resultSet.getLong("c.discord_ID");

                returnList.add(new ReturnNowPlaying(discordId, lastfmId, artistName + " - " + albumName, playNumber));
            }
            /* Return booking. */
            return new WrapperReturnNowPlaying(returnList, returnList.size(), url, artistName);

        } catch (SQLException e) {
            throw new ChuuServiceException(e);
        }
    }

    @Override
    public WrapperReturnNowPlaying whoKnowsTrack(Connection con, long trackId, long guildId, int limit) {

        @Language("MariaDB")
        String queryString =
                "SELECT a2.track_name,a3.name, a.lastfm_id, a.playNumber, coalesce(a2.url,a3.url) as turl, c.discord_id " +
                        "FROM  scrobbled_track a" +
                        " JOIN track a2 ON a.track_id = a2.id  " +
                        " join artist a3 on a2.artist_id = a3.id " +
                        "JOIN `user` c on c.lastFm_Id = a.lastFM_ID " +
                        "JOIN user_guild d on c.discord_ID = d.discord_Id " +
                        "where d.guild_Id = ? " +
                        "and  a2.id = ? " +
                        "ORDER BY a.playNumber desc ";
        queryString = limit == Integer.MAX_VALUE ? queryString : queryString + "limit " + limit;
        try (PreparedStatement preparedStatement = con.prepareStatement(queryString)) {

            int i = 1;
            preparedStatement.setLong(i++, guildId);
            preparedStatement.setLong(i, trackId);
            /* Execute query. */

            ResultSet resultSet = preparedStatement.executeQuery();
            String url = "";
            String artistName = "";
            String trackName;
            List<ReturnNowPlaying> returnList = new ArrayList<>();



            /* Get results. */
            int j = 0;
            while (resultSet.next() && (j < limit)) {
                url = resultSet.getString("turl");
                artistName = resultSet.getString("a3.name");
                trackName = resultSet.getString("a2.track_name");

                String lastfmId = resultSet.getString("a.lastFM_ID");

                int playNumber = resultSet.getInt("a.playNumber");
                long discordId = resultSet.getLong("c.discord_ID");

                returnList.add(new ReturnNowPlaying(discordId, lastfmId, artistName + " - " + trackName, playNumber));
            }
            /* Return booking. */
            return new WrapperReturnNowPlaying(returnList, returnList.size(), url, artistName);

        } catch (SQLException e) {
            throw new ChuuServiceException(e);
        }
    }

    @Override
    public WrapperReturnNowPlaying globalWhoKnowsAlbum(Connection con, long albumId, int limit, long ownerId, boolean includeBottedUsers) {

        @Language("MariaDB")
        String queryString =
                "SELECT a2.album_name,a3.name, a.lastfm_id, a.playNumber, a2.url, c.discord_id,c.privacy_mode " +
                        "FROM  scrobbled_album a" +
                        " JOIN album a2 ON a.album_id = a2.id  " +
                        " join artist a3 on a2.artist_id = a3.id " +
                        "JOIN `user` c on c.lastFm_Id = a.lastFM_ID " +
                        " where   (? or not c.botted_account or c.discord_id = ? )  " +
                        "and  a2.id = ? " +
                        "ORDER BY a.playNumber desc ";
        queryString = limit == Integer.MAX_VALUE ? queryString : queryString + "limit " + limit;
        try (PreparedStatement preparedStatement = con.prepareStatement(queryString)) {

            int i = 1;
            preparedStatement.setBoolean(i++, includeBottedUsers);
            preparedStatement.setLong(i++, ownerId);
            preparedStatement.setLong(i, albumId);






            /* Execute query. */

            ResultSet resultSet = preparedStatement.executeQuery();
            String url = "";
            String artistName = "";
            String albumName = "";

            List<ReturnNowPlaying> returnList = new ArrayList<>();



            /* Get results. */
            int j = 0;
            while (resultSet.next() && (j < limit)) {
                url = resultSet.getString("a2.url");
                artistName = resultSet.getString("a3.name");
                albumName = resultSet.getString("a2.album_name");

                String lastfmId = resultSet.getString("a.lastFM_ID");

                int playNumber = resultSet.getInt("a.playNumber");
                long discordId = resultSet.getLong("c.discord_ID");
                PrivacyMode privacyMode = PrivacyMode.valueOf(resultSet.getString("c.privacy_mode"));

                returnList.add(new GlobalReturnNowPlayingAlbum(discordId, lastfmId, artistName, playNumber, privacyMode, albumName));
            }
            /* Return booking. */
            return new WrapperReturnNowPlaying(returnList, returnList.size(), url, artistName + " - " + albumName);

        } catch (SQLException e) {
            throw new ChuuServiceException(e);
        }
    }

    @Override
    public UniqueWrapper<AlbumPlays> albumUniques(Connection connection, long guildID, String lastfmId) {
        @Language("MariaDB") String queryString = "SELECT * " +
                "FROM(  " +
                "       SELECT a2.album_name,a3.name, playnumber, a.lastfm_id ,b.discord_id" +
                "       FROM scrobbled_album a JOIN user b " +
                "       ON a.lastfm_id = b.lastfm_id " +
                "       JOIN user_guild c ON b.discord_id = c.discord_id " +
                " JOIN album a2 ON a.album_id = a2.id " +
                " join artist a3 on a2.artist_id = a3.id " +
                "       WHERE c.guild_id = ? AND a.playnumber > 2 " +
                "       GROUP BY a.album_id " +
                "       HAVING count( *) = 1) temp " +
                "WHERE temp.lastfm_id = ? AND temp.playnumber > 1 " +
                " ORDER BY temp.playnumber DESC ";

        try (PreparedStatement preparedStatement = connection.prepareStatement(queryString)) {
            int i = 1;
            preparedStatement.setLong(i++, guildID);
            preparedStatement.setString(i, lastfmId);
            ResultSet resultSet = preparedStatement.executeQuery();

            List<AlbumPlays> returnList = new ArrayList<>();
            long discordId = 0;
            while (resultSet.next()) {
                discordId = resultSet.getLong("temp.discord_id");
                String album = resultSet.getString("temp.album_name");
                String artist = resultSet.getString("temp.name");

                int countA = resultSet.getInt("temp.playNumber");

                returnList.add(new AlbumPlays(artist, countA, album));

            }
            return new UniqueWrapper<>(returnList.size(), discordId, lastfmId, returnList);


        } catch (SQLException e) {
            logger.warn(e.getMessage(), e);
            throw new ChuuServiceException(e);
        }
    }

    @Override
    public BotStats getBotStats(Connection connection) {
        @Language("MariaDB") String queryString = "Select (Select count(*) from user) as user_count," +
                " (Select count(*) from guild) guild_count," +
                " (select table_rows from  information_schema.TABLES WHERE TABLES.TABLE_SCHEMA = 'lastfm' and TABLE_NAME = 'artist') artist_count," +
                "(select table_rows from  information_schema.TABLES WHERE TABLES.TABLE_SCHEMA = 'lastfm' and TABLE_NAME = 'album') album_count," +
                "(select sum(playnumber) from scrobbled_artist) scrobbled_count," +
                "(select table_rows from  information_schema.TABLES WHERE TABLES.TABLE_SCHEMA = 'lastfm' and TABLE_NAME = 'album_rating' ) rym_count," +
                " (select avg(rating) from album_rating ) rym_avg," +
                " (select count(*) from past_recommendations) recommedation_count," +
                " (Select count(*) from corrections)correction_count, " +
                "(select count(*) from randomlinks) random_count," +
                " (select table_rows from  information_schema.TABLES WHERE TABLES.TABLE_SCHEMA = 'lastfm' and TABLE_NAME = 'alt_url') image_count, " +
                "(select count(*) from vote) vote_count," +
                "(select count(*) from user_guild) set_count," +
                "(select  value from metrics where id = 5) as api_count";

        try (PreparedStatement preparedStatement = connection.prepareStatement(queryString)) {
            ResultSet resultSet = preparedStatement.executeQuery();

            if (resultSet.next()) {
                long user_count = resultSet.getLong("user_count");
                long guild_count = resultSet.getLong("guild_count");
                long artist_count = resultSet.getLong("artist_count");
                long album_count = resultSet.getLong("album_count");
                long scrobbled_count = resultSet.getLong("scrobbled_count");
                long rym_count = resultSet.getLong("rym_count");
                double rym_avg = resultSet.getDouble("rym_avg");
                long recommendation_count = resultSet.getLong("recommedation_count");
                long correction_count = resultSet.getLong("correction_count");
                long random_count = resultSet.getLong("random_count");
                long image_count = resultSet.getLong("image_count");
                long vote_count = resultSet.getLong("vote_count");
                long set_count = resultSet.getLong("set_count");
                long api_count = resultSet.getLong("api_count");

                return new BotStats(user_count, guild_count, artist_count, album_count, scrobbled_count, rym_count, rym_avg, recommendation_count, correction_count, random_count, image_count, vote_count, set_count, api_count);

            }
            throw new ChuuServiceException();
        } catch (SQLException e) {
            logger.warn(e.getMessage(), e);
            throw new ChuuServiceException(e);
        }

    }

    @Override
    public long getUserAlbumCount(Connection con, long discordId) {
        @Language("MariaDB") String queryString = "SELECT count(*) AS numb FROM scrobbled_album a join user b on a.lastfm_id = b.lastfm_id WHERE b.discord_id= ? ";
        try (PreparedStatement preparedStatement = con.prepareStatement(queryString)) {
            int i = 1;
            preparedStatement.setLong(i, discordId);

            /* Execute query. */
            ResultSet resultSet = preparedStatement.executeQuery();
            if (!resultSet.next())
                return 0;
            return resultSet.getInt("numb");


        } catch (SQLException e) {
            throw new ChuuServiceException(e);
        }

    }

    @Override
    public List<StreakEntity> getUserStreaks(long discordId, Connection connection) {
        List<StreakEntity> returnList = new ArrayList<>();
        @Language("MariaDB") String queryString = "SELECT artist_combo,album_combo,track_combo,b.name,c.album_name,track_name " +
                "FROM top_combos a join artist b on a.artist_id = b.id left join album c on a.album_id = c.id where " +
                "discord_id = ? order by  artist_combo desc,album_combo desc, track_combo desc ";
        try (PreparedStatement preparedStatement = connection.prepareStatement(queryString)) {
            int i = 1;
            preparedStatement.setLong(1, discordId);

            /* Execute query. */
            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                int artistCombo = resultSet.getInt("artist_combo");
                int albumCombo = resultSet.getInt("album_combo");
                int trackCombo = resultSet.getInt("track_combo");
                String artistName = resultSet.getString("name");
                String trackName = resultSet.getString("track_name");

                String albumName = resultSet.getString("album_name");


                StreakEntity streakEntity = new StreakEntity(artistName, artistCombo, albumName, albumCombo, trackName, trackCombo, null, null);
                returnList.add(streakEntity);
            }

        } catch (SQLException e) {
            throw new ChuuServiceException(e);
        }
        return returnList;

    }

    @Override
    public List<GlobalStreakEntities> getTopStreaks(Connection connection, @Nullable Long comboFilter, @Nullable Long guildId) {
        List<GlobalStreakEntities> returnList = new ArrayList<>();
        @Language("MariaDB") String queryString = "SELECT artist_combo,album_combo,track_combo,b.name,c.album_name,track_name,privacy_mode,a.discord_id,d.lastfm_id " +
                "FROM top_combos a join artist b on a.artist_id = b.id left join album c on a.album_id = c.id join user d on a.discord_id = d.discord_id    ";

        if (guildId != null) {
            queryString += " join user_guild e on d.discord_id = e.discord_id where e.guild_id = ? ";
        } else {
            queryString += " where 1=1";
        }

        if (comboFilter != null) {
            queryString += " and artist_combo > ? ";
        }

        queryString += " order by  artist_combo desc,album_combo desc, track_combo desc ";
        try (PreparedStatement preparedStatement = connection.prepareStatement(queryString)) {
            int i = 1;
            if (guildId != null)
                preparedStatement.setLong(i++, guildId);
            if (comboFilter != null)
                preparedStatement.setLong(i, comboFilter);

            /* Execute query. */
            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                int artistCombo = resultSet.getInt("artist_combo");
                int albumCombo = resultSet.getInt("album_combo");
                int trackCombo = resultSet.getInt("track_combo");
                String artistName = resultSet.getString("name");
                String trackName = resultSet.getString("track_name");

                String albumName = resultSet.getString("album_name");
                PrivacyMode privacyMode = PrivacyMode.valueOf(resultSet.getString("privacy_mode"));
                long discordId = resultSet.getLong("discord_id");
                String lastfm_id = resultSet.getString("lastfm_id");


                GlobalStreakEntities streakEntity = new GlobalStreakEntities(artistName, artistCombo, albumName, albumCombo, trackName, trackCombo, null, privacyMode, discordId, lastfm_id);
                returnList.add(streakEntity);
            }

        } catch (SQLException e) {
            throw new ChuuServiceException(e);
        }
        return returnList;

    }

    @Override
    public String getReverseCorrection(Connection connection, String correction) {

        @Language("MariaDB") String queryString = "select alias,min(a.id)  as ird " +
                "from corrections a join artist b on a.artist_id = b.id  join scrobbled_artist c on b.id = c.artist_id" +
                " where b.name = ? " +
                "order by ird desc " +
                "limit 1";

        try (PreparedStatement preparedStatement = connection.prepareStatement(queryString)) {
            int i = 1;
            preparedStatement.setString(i, correction);
            ResultSet resultSet = preparedStatement.executeQuery();

            if (resultSet.next()) {
                return resultSet.getString("alias");
            }
            return correction;

        } catch (SQLException e) {
            logger.warn(e.getMessage(), e);
            throw new ChuuServiceException(e);
        }

    }

    @Override
    public List<GlobalStreakEntities> getArtistTopStreaks(Connection connection, Long comboFilter, Long guildId, long artistId, Integer limit) {
        List<GlobalStreakEntities> returnList = new ArrayList<>();
        @Language("MariaDB") String queryString = "SELECT artist_combo,album_combo,track_combo,b.name,c.album_name,track_name,privacy_mode,a.discord_id,d.lastfm_id" +
                " FROM top_combos a join artist b on a.artist_id = b.id left join album c on a.album_id = c.id join user d on a.discord_id = d.discord_id    ";

        if (guildId != null) {
            queryString += " join user_guild e on d.discord_id = e.discord_id where e.guild_id = ? ";
        } else {
            queryString += " where 1=1";
        }

        if (comboFilter != null) {
            queryString += " and artist_combo > ? ";
        }
        queryString += " and a.artist_id = ? ";

        queryString += " order by  artist_combo desc,album_combo desc, track_combo desc ";
        if (limit != null) {
            queryString += " limit " + limit;
        }
        try (PreparedStatement preparedStatement = connection.prepareStatement(queryString)) {
            int i = 1;
            if (guildId != null)
                preparedStatement.setLong(i++, guildId);
            if (comboFilter != null)
                preparedStatement.setLong(i, comboFilter);
            preparedStatement.setLong(i, artistId);

            /* Execute query. */
            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                int artistCombo = resultSet.getInt("artist_combo");
                int albumCombo = resultSet.getInt("album_combo");
                int trackCombo = resultSet.getInt("track_combo");
                String artistName = resultSet.getString("name");
                String trackName = resultSet.getString("track_name");

                String albumName = resultSet.getString("album_name");
                PrivacyMode privacyMode = PrivacyMode.valueOf(resultSet.getString("privacy_mode"));
                long discordId = resultSet.getLong("discord_id");
                String lastfm_id = resultSet.getString("lastfm_id");


                GlobalStreakEntities streakEntity = new GlobalStreakEntities(artistName, artistCombo, albumName, albumCombo, trackName, trackCombo, null, privacyMode, discordId, lastfm_id);
                returnList.add(streakEntity);
            }

        } catch (SQLException e) {
            throw new ChuuServiceException(e);
        }
        return returnList;


    }

    @Override
    public List<StreakEntity> getUserArtistTopStreaks(Connection connection, long artistId, Integer limit, long discordId) {
        List<StreakEntity> returnList = new ArrayList<>();
        @Language("MariaDB") String queryString = "SELECT artist_combo,album_combo,track_combo,b.name,c.album_name,track_name" +
                " FROM top_combos a join artist b on a.artist_id = b.id left join album c on a.album_id = c.id     ";


        queryString += " where 1=1";

        queryString += " and a.artist_id = ? and a.discord_id = ? ";

        queryString += " order by  artist_combo desc,album_combo desc, track_combo desc ";
        if (limit != null) {
            queryString += " limit " + limit;
        }
        try (PreparedStatement preparedStatement = connection.prepareStatement(queryString)) {
            int i = 1;
            preparedStatement.setLong(i++, artistId);
            preparedStatement.setLong(i, discordId);

            /* Execute query. */
            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                int artistCombo = resultSet.getInt("artist_combo");
                int albumCombo = resultSet.getInt("album_combo");
                int trackCombo = resultSet.getInt("track_combo");
                String artistName = resultSet.getString("name");
                String trackName = resultSet.getString("track_name");

                String albumName = resultSet.getString("album_name");


                StreakEntity streakEntity = new StreakEntity(artistName, artistCombo, albumName, albumCombo, trackName, trackCombo, null, null);
                returnList.add(streakEntity);
            }

        } catch (SQLException e) {
            throw new ChuuServiceException(e);
        }
        return returnList;

    }

    @Override
    public List<ScoredAlbumRatings> getServerTopRandomUrls(Connection connection, long guildId) {

        List<ScoredAlbumRatings> returnList = new ArrayList<>();

        String s = "select *  from (select  count(*) as  coun,  avg(rating) as ave, a.url " +
                "from random_links_ratings a " +
                "join user_guild d on a.discord_id = d.discord_id " +
                " where guild_id = ? " +
                "group by a.url) main " +
                "order by ((0.5 * main.ave) + 10 * (1 - 0.5) * (1 - (EXP(-main.coun/5))))  desc limit 200";
        try (PreparedStatement preparedStatement = connection.prepareStatement(s)) {
            preparedStatement.setLong(1, guildId);
            ResultSet resultSet = preparedStatement.executeQuery();
            getScoredAlbums(returnList, resultSet);
        } catch (
                SQLException throwables) {

            throw new ChuuServiceException(throwables);
        }
        return returnList;
    }

    @Override
    public List<ScoredAlbumRatings> getTopUrlsRatedByUser(Connection connection, long discordId) {

        List<ScoredAlbumRatings> returnList = new ArrayList<>();

        String s = "select *  from (select  count(*) as  coun,  avg(rating) as ave, a.url " +
                "from random_links_ratings a " +
                " where discord_id  = ? " +
                "group by a.url) main " +
                "order by ((0.5 * main.ave) + 10 * (1 - 0.5) * (1 - (EXP(-main.coun/5))))  desc limit 200";
        try (PreparedStatement preparedStatement = connection.prepareStatement(s)) {
            preparedStatement.setLong(1, discordId);
            ResultSet resultSet = preparedStatement.executeQuery();
            getScoredAlbums(returnList, resultSet);
        } catch (
                SQLException throwables) {

            throw new ChuuServiceException(throwables);
        }
        return returnList;
    }

    @Override
    public List<ScoredAlbumRatings> getGlobalTopRandomUrls(Connection connection) {

        List<ScoredAlbumRatings> returnList = new ArrayList<>();

        String s = "select *  from (select  count(*) as  coun,  avg(rating) as ave, a.url " +
                "from random_links_ratings a " +
                "group by a.url) main " +
                "order by ((0.5 * main.ave) + 10 * (1 - 0.5) * (1 - (EXP(-main.coun/5))))  desc limit 200";
        try (PreparedStatement preparedStatement = connection.prepareStatement(s)) {
            ResultSet resultSet = preparedStatement.executeQuery();
            getScoredAlbums(returnList, resultSet);
        } catch (
                SQLException throwables) {

            throw new ChuuServiceException(throwables);
        }
        return returnList;
    }

    @Override
    public List<ScoredAlbumRatings> getUserTopRatedUrlsByEveryoneElse(Connection connection, long discordId) {
        List<ScoredAlbumRatings> returnList = new ArrayList<>();

        String s = "select *  from (select  count(*) as  coun,  avg(rating) as ave, a.url " +
                "from random_links_ratings a " +
                "join randomlinks b on a.url = b.url  " +
                " where b.discord_id = ? " +
                "group by a.url) main " +
                "order by ((0.5 * main.ave) + 10 * (1 - 0.5) * (1 - (EXP(-main.coun/5))))  desc limit 200";
        try (PreparedStatement preparedStatement = connection.prepareStatement(s)) {
            preparedStatement.setLong(1, discordId);
            ResultSet resultSet = preparedStatement.executeQuery();
            getScoredAlbums(returnList, resultSet);
        } catch (
                SQLException throwables) {

            throw new ChuuServiceException(throwables);
        }
        return returnList;
    }

    @Override
    public Set<String> getPrivateLastfmIds(Connection connection) {

        Set<String> returnList = new HashSet<>();

        String s = "select lastfm_id from user where private_lastfm";
        try (PreparedStatement preparedStatement = connection.prepareStatement(s)) {
            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                returnList.add(resultSet.getString(1));
            }
        } catch (
                SQLException throwables) {

            throw new ChuuServiceException(throwables);
        }
        return returnList;
    }

    @Override
    public List<ScrobbledAlbum> getUserAlbums(Connection connection, String lastfmId) {

        List<ScrobbledAlbum> scrobbledAlbums = new ArrayList<>();
        String s = "select b.album_name,c.name,b.url,b.mbid,a.playnumber  from scrobbled_album a join album b on a.album_id = b.id join artist c on a.artist_id = c.id  where a.lastfm_id = ? order by a.playnumber desc";
        try (PreparedStatement preparedStatement = connection.prepareStatement(s)) {
            preparedStatement.setString(1, lastfmId);
            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                String album = resultSet.getString(1);
                String artist = resultSet.getString(2);
                String url = resultSet.getString(3);
                String mbid = resultSet.getString(4);
                int playnumber = resultSet.getInt(5);

                ScrobbledAlbum scrobbledAlbum = new ScrobbledAlbum(album, artist, url, mbid);
                scrobbledAlbum.setCount(playnumber);
                scrobbledAlbums.add(scrobbledAlbum);
            }
        } catch (
                SQLException throwables) {

            throw new ChuuServiceException(throwables);
        }
        return scrobbledAlbums;
    }

    @Override
    public ResultWrapper<ScrobbledAlbum> getGuildTopAlbum(Connection connection, Long guildID, int limit, boolean doCount) {

        @Language("MariaDB") String normalQUery = "SELECT d.name,e.album_name, sum(playnumber) AS orden ,e.url,e.mbid  ";

        String countQuery = "Select count(*) as orden ";


        String queryBody = "FROM  scrobbled_album a" +
                " JOIN user b" +
                " ON a.lastfm_id = b.lastfm_id" +
                " JOIN artist d " +
                " ON a.artist_id = d.id" +
                " join album e on a.album_id = e.id";
        if (guildID != null) {
            queryBody += " JOIN  user_guild c" +
                    " ON b.discord_id=c.discord_id" +
                    " WHERE c.guild_id = ?";
        }

        List<ScrobbledAlbum> list = new ArrayList<>();
        int count = 0;
        int i = 1;
        try (PreparedStatement preparedStatement1 = connection.prepareStatement(normalQUery + queryBody + " GROUP BY album_id,url  ORDER BY orden DESC  limit ?")) {
            if (guildID != null)
                preparedStatement1.setLong(i++, guildID);

            preparedStatement1.setInt(i, limit);

            ResultSet resultSet1 = preparedStatement1.executeQuery();

            while (resultSet1.next()) {
                String artist = resultSet1.getString("d.name");
                String album = resultSet1.getString("e.album_name");
                String mbid = resultSet1.getString("e.mbid");


                String url = resultSet1.getString("e.url");

                int plays = resultSet1.getInt("orden");
                ScrobbledAlbum who = new ScrobbledAlbum(album, artist, url, mbid);
                who.setCount(plays);
                list.add(who);
            }
            if (doCount) {

                PreparedStatement preparedStatement = connection.prepareStatement(countQuery + queryBody);
                i = 1;
                if (guildID != null)
                    preparedStatement.setLong(i, guildID);

                ResultSet resultSet = preparedStatement.executeQuery();
                if (resultSet.next()) {
                    count = resultSet.getInt(1);
                }


            }
        } catch (SQLException e) {
            logger.warn(e.getMessage(), e);
            throw new ChuuServiceException(e);
        }
        return new ResultWrapper<>(count, list);
    }

    @Override
    public List<ScrobbledArtist> getTopTag(Connection connection, String genre, @Nullable Long guildId, int limit) {
        List<ScrobbledArtist> scrobbledArtists = new ArrayList<>();
        String queryString = " " +
                "Select a.artist_id,sum(playNumber) plays,b.url,b.name" +
                " from scrobbled_artist a " +
                "join artist_tags e on a.artist_id = e.artist_id  " +
                "join artist b on a.artist_id = b.id" +
                " join user c on a.lastfm_id = c.lastfm_id ";

        if (guildId != null) {
            queryString += " join user_guild d on c.discord_id = d.discord_id " +
                    "where e.tag = ? and d.guild_id = ?";
        } else {
            queryString += "where e.tag = ? ";

        }
        queryString += " group by a.artist_id " +
                "order by plays desc limit ?";
        try (
                PreparedStatement preparedStatement = connection.prepareStatement(queryString)) {
            int i = 1;
            preparedStatement.setString(i++, genre);
            if (guildId != null) {
                preparedStatement.setLong(i++, guildId);
            }
            preparedStatement.setInt(i, limit);

            ResultSet resultSet = preparedStatement.executeQuery();

            while (resultSet.next()) {
                String artistName = resultSet.getString("b.name");
                String artistUrl = resultSet.getString("b.url");
                int plays = resultSet.getInt("plays");
                long artist_id = resultSet.getInt("a.artist_id");

                ScrobbledArtist value = new ScrobbledArtist(null, artistName, plays);
                value.setUrl(artistUrl);
                value.setArtistId(artist_id);
                scrobbledArtists.add(value);
            }
        } catch (SQLException e) {
            throw new ChuuServiceException(e);
        }
        return scrobbledArtists;
    }

    @Override
    public WrapperReturnNowPlaying whoKnowsTag(Connection connection, String genre, long guildId, int limit) {

        @Language("MariaDB")
        String queryString =
                "SELECT b.lastfm_id, sum(b.playnumber) plays, c.discord_id " +
                        "FROM  artist_tags a " +
                        "JOIN artist a2 ON a.artist_id = a2.id  " +
                        "JOIN scrobbled_artist b on b.artist_id = a2.id " +
                        "JOIN `user` c on c.lastfm_id = b.lastfm_id " +
                        "JOIN user_guild d on c.discord_ID = d.discord_Id " +
                        "where d.guild_Id = ? " +
                        "and  a.tag = ? " +
                        " group by b.lastfm_id,c.discord_id " +
                        "ORDER BY plays desc ";
        queryString = limit == Integer.MAX_VALUE ? queryString : queryString + "limit " + limit;
        try (PreparedStatement preparedStatement = connection.prepareStatement(queryString)) {

            int i = 1;
            preparedStatement.setLong(i++, guildId);
            preparedStatement.setString(i, genre);


            ResultSet resultSet = preparedStatement.executeQuery();
            List<ReturnNowPlaying> returnList = new ArrayList<>();


            int j = 0;
            while (resultSet.next() && (j < limit)) {
                String lastfmId = resultSet.getString("b.lastfm_id");
                int playNumber = resultSet.getInt("plays");
                long discordId = resultSet.getLong("c.discord_ID");
                returnList.add(new ReturnNowPlaying(discordId, lastfmId, null, playNumber));
            }
            return new WrapperReturnNowPlaying(returnList, returnList.size(), null, genre);

        } catch (SQLException e) {
            throw new ChuuServiceException(e);
        }
    }

    @Override
    public Set<String> getBannedTags(Connection connection) {
        Set<String> bannedTags = new HashSet<>();
        String queryString = "Select tag from banned_tags";
        try (
                PreparedStatement preparedStatement = connection.prepareStatement(queryString)) {
            ResultSet resultSet = preparedStatement.executeQuery();

            while (resultSet.next()) {
                bannedTags.add(resultSet.getString(1).toLowerCase());
            }
        } catch (SQLException e) {
            throw new ChuuServiceException(e);
        }
        return bannedTags;
    }

    private String prepareINQuery(int size) {
        return String.join(",", Collections.nCopies(size, "(?,?)"));
    }


    @Override
    public List<AlbumInfo> getAlbumsWithTag(Connection connection, List<AlbumInfo> albums, long discordId, String tag) {
        String queryString = "SELECT album_name,c.name,a.mbid,c.mbid as artist_mbid " +
                "FROM album a " +
                "join artist c on a.artist_id = c.id " +
                "join scrobbled_album d on a.id = d.album_id " +
                "join user e on d.lastfm_id = e.lastfm_id  " +
                "join  album_tags b on a.id = b.album_id " +
                "WHERE (c.name,album_name)  IN (%s) and tag = ? and e.discord_id = ? ";
        String sql = String.format(queryString, albums.isEmpty() ? null : prepareINQuery(albums.size()));
        List<AlbumInfo> returnInfoes = new ArrayList<>();
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {


            for (int i = 0; i < albums.size(); i++) {
                preparedStatement.setString(2 * i + 1, albums.get(i).getArtist());
                preparedStatement.setString(2 * i + 2, albums.get(i).getName());
            }
            preparedStatement.setString(albums.size() * 2 + 1, tag);
            preparedStatement.setLong(albums.size() * 2 + 2, discordId);


            ResultSet resultSet = preparedStatement.executeQuery();

            while (resultSet.next()) {
                String name = resultSet.getString("name");
                String albumName = resultSet.getString("album_name");
                String albumMbid = resultSet.getString("mbid");
                String artistMbid = resultSet.getString("artist_mbid");
                AlbumInfo e = new AlbumInfo(albumMbid, albumName, name);
                returnInfoes.add(e);

            }
        } catch (SQLException e) {
            throw new ChuuServiceException(e);
        }
        return returnInfoes;
    }

    @Override
    public List<ScrobbledArtist> getUserArtists(Connection connection, String lastfmId) {

        List<ScrobbledArtist> scrobbledAlbums = new ArrayList<>();
        String s = "select name,b.url,b.mbid,a.playnumber  from scrobbled_artist a join artist b on a.artist_id = b.id   where a.lastfm_id = ? order by a.playnumber desc";
        try (PreparedStatement preparedStatement = connection.prepareStatement(s)) {
            preparedStatement.setString(1, lastfmId);
            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                String artist = resultSet.getString(1);
                String url = resultSet.getString(2);
                String mbid = resultSet.getString(3);
                int playnumber = resultSet.getInt(4);
                ScrobbledArtist scrobbledArtist = new ScrobbledArtist(artist, playnumber, url);
                scrobbledArtist.setArtistMbid(mbid);
                scrobbledArtist.setCount(playnumber);
                scrobbledAlbums.add(scrobbledArtist);
            }
        } catch (
                SQLException throwables) {

            throw new ChuuServiceException(throwables);
        }
        return scrobbledAlbums;
    }

    @Override
    public List<ArtistInfo> getArtistWithTag(Connection connection, List<ArtistInfo> artists, long discordId, String genre) {
        String queryString = "SELECT c.name,c.url,c.mbid as artist_mbid " +
                "FROM artist c " +
                "join scrobbled_artist d on c.id = d.artist_id " +
                "join user e on d.lastfm_id = e.lastfm_id  " +
                "join  artist_tags b on c.id = b.artist_id " +
                "WHERE (c.name)  IN (%s) and tag = ? and e.discord_id = ? ";
        String sql = String.format(queryString, artists.isEmpty() ? null : String.join(",", Collections.nCopies(artists.size(), "?")));
        List<ArtistInfo> returnInfoes = new ArrayList<>();
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {


            for (int i = 0; i < artists.size(); i++) {
                preparedStatement.setString(i + 1, artists.get(i).getArtist());
            }
            preparedStatement.setString(artists.size() + 1, genre);
            preparedStatement.setLong(artists.size() + 2, discordId);


            ResultSet resultSet = preparedStatement.executeQuery();

            while (resultSet.next()) {
                String name = resultSet.getString("name");
                String artistMbid = resultSet.getString("artist_mbid");
                String url = resultSet.getString("url");

                ArtistInfo e = new ArtistInfo(url, name, artistMbid);
                returnInfoes.add(e);

            }
        } catch (SQLException e) {
            throw new ChuuServiceException(e);
        }
        return returnInfoes;

    }

    @Override
    public Map<Genre, Integer> genreCountsByArtist(Connection connection, List<ArtistInfo> artistInfos) {

        @Language("MariaDB") String queryString = "SELECT tag,count(*) as coun FROM artist a join artist_tags b  on a.id = b.artist_id WHERE name IN (%s) group by b.tag";
        String sql = String.format(queryString, artistInfos.isEmpty() ? null : preparePlaceHolders(artistInfos.size()));


        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            for (int i = 0; i < artistInfos.size(); i++) {
                preparedStatement.setString(i + 1, artistInfos.get(i).getArtist());
            }

            Map<Genre, Integer> returnList = new HashMap<>();
            /* Execute query. */
            ResultSet resultSet = preparedStatement.executeQuery();

            while (resultSet.next()) {

                String tag = resultSet.getString("tag");
                int count = resultSet.getInt("coun");

                returnList.put(new Genre(tag, null), count);

            }
            return returnList;
        } catch (SQLException e) {
            throw new ChuuServiceException(e);
        }
    }

    @Override
    public List<WrapperReturnNowPlaying> whoknowsSet(Connection connection, Set<String> artists, long guildId, int limit, @Nullable String user) {
        Map<String, String> urlMap = new HashMap<>();

        @Language("MariaDB")
        String queryString =
                "SELECT a2.name, a.lastfm_id, a.playNumber, a2.url, c.discord_id " +
                        "FROM  scrobbled_artist a" +
                        " JOIN artist a2 ON a.artist_id = a2.id  " +
                        "JOIN `user` c on c.lastFm_Id = a.lastFM_ID " +
                        "JOIN user_guild d on c.discord_ID = d.discord_Id " +
                        "where d.guild_Id = ? " +
                        "and  (name) in  (%s) " +
                        "ORDER BY a.playNumber desc ";
        String sql = String.format(queryString, artists.isEmpty() ? null : preparePlaceHolders(artists.size()));

        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {

            int i = 1;
            preparedStatement.setLong(i, guildId);

            for (String artist : artists) {
                preparedStatement.setString(++i, artist);
            }


            ResultSet resultSet = preparedStatement.executeQuery();
            String url;
            String artistName;
            List<ReturnNowPlaying> returnList = new ArrayList<>();



            /* Get results. */
            int j = 0;
            while (resultSet.next() && (j < limit)) {
                String lastfmId = resultSet.getString("a.lastFM_ID");
                if (user != null) {
                    if (!lastfmId.equals(user)) {
                        continue;
                    }
                }
                url = resultSet.getString("a2.url");
                artistName = resultSet.getString("a2.name");
                urlMap.put(artistName, url);
                int playNumber = resultSet.getInt("a.playNumber");
                long discordId = resultSet.getLong("c.discord_ID");

                returnList.add(new ReturnNowPlaying(discordId, lastfmId, artistName, playNumber));

            }

            return returnList
                    .stream().collect(
                            Collectors.collectingAndThen(
                                    Collectors.groupingBy(ReturnNowPlaying::getArtist,
                                            Collectors.collectingAndThen(
                                                    Collectors.toList(), list -> {
                                                        if (list.isEmpty()) {
                                                            return null;
                                                        }
                                                        ReturnNowPlaying a = list.get(0);
                                                        return new WrapperReturnNowPlaying(list, list.size(), urlMap.get(a.getArtist()), a.getArtist());
                                                    })),
                                    result -> result.values().stream().filter(Objects::nonNull)

                                            .sorted(Comparator.comparingInt
                                                    ((WrapperReturnNowPlaying t) ->
                                                            t.getReturnNowPlayings().stream().mapToInt(ReturnNowPlaying::getPlayNumber).sum()).reversed())
                                            .limit(limit)
                                            .collect(Collectors.toList())));

        } catch (SQLException e) {
            throw new ChuuServiceException(e);
        }

    }

    @Override
    public WrapperReturnNowPlaying whoknowsTagsSet(Connection connection, Set<String> tags, long guildId, int limit, String user, SearchMode searchMode) {

        @Language("MariaDB")
        String queryString =
                """
                        SELECT b.lastfm_id, sum(b.playnumber) plays, c.discord_id\s
                        FROM  artist_tags a\s
                        JOIN artist a2 ON a.artist_id = a2.id \s
                        JOIN scrobbled_artist b on b.artist_id = a2.id\s
                        JOIN `user` c on c.lastfm_id = b.lastfm_id\s
                        JOIN user_guild d on c.discord_ID = d.discord_Id\s
                        where d.guild_Id = ?\s
                        and  (tag) in  (%s)\s
                         group by b.lastfm_id,c.discord_id\s
                        """;

        if (searchMode == SearchMode.EXCLUSIVE) {
            queryString += " having count(distinct tag) = ? ";

        }
        queryString += "ORDER BY plays desc ";
        String sql = String.format(queryString, tags.isEmpty() ? null : preparePlaceHolders(tags.size()));

        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {

            int i = 1;
            preparedStatement.setLong(i, guildId);

            for (String artist : tags) {
                preparedStatement.setString(++i, artist);
            }
            if (searchMode == SearchMode.EXCLUSIVE) {
                preparedStatement.setInt(++i, tags.size());
            }


            ResultSet resultSet = preparedStatement.executeQuery();
            String url = "";
            String artistName = "";
            List<ReturnNowPlaying> returnList = new ArrayList<>();


            int j = 0;
            while (resultSet.next() && (j < limit)) {
                String lastfmId = resultSet.getString("b.lastfm_id");
                int playNumber = resultSet.getInt("plays");
                long discordId = resultSet.getLong("c.discord_ID");
                returnList.add(new ReturnNowPlaying(discordId, lastfmId, null, playNumber));
            }
            return new WrapperReturnNowPlaying(returnList, returnList.size(), null, null);


        } catch (SQLException e) {
            throw new ChuuServiceException(e);
        }
    }

    @Override
    public List<ScrobbledArtist> getTopTagSet(Connection connection, Set<String> tags, Long guildId, int limit, SearchMode mode) {
        List<ScrobbledArtist> scrobbledArtists = new ArrayList<>();
        String queryString = " " +
                "Select a.artist_id,sum(playNumber) plays,b.url,b.name" +
                " from scrobbled_artist a " +
                "join artist_tags e on a.artist_id = e.artist_id  " +
                "join artist b on a.artist_id = b.id" +
                " join user c on a.lastfm_id = c.lastfm_id ";

        if (guildId != null) {
            queryString += " join user_guild d on c.discord_id = d.discord_id " +
                    "where e.tag in (%s) and d.guild_id = ?";
        } else {
            queryString += "where e.tag in (%s) and d.guild_id = ?";

        }
        queryString += " group by a.artist_id ";
        if (mode == SearchMode.EXCLUSIVE) {
            queryString += " having count(distinct tag) = ? ";

        }
        queryString += "order by plays desc limit ?";
        String sql = String.format(queryString, tags.isEmpty() ? null : preparePlaceHolders(tags.size()));

        try (
                PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            int i = 0;

            for (String tag : tags) {
                preparedStatement.setString(++i, tag);
            }
            if (guildId != null) {
                preparedStatement.setLong(++i, guildId);
            }
            if (mode == SearchMode.EXCLUSIVE) {
                preparedStatement.setInt(++i, tags.size());
            }

            preparedStatement.setInt(++i, limit);

            ResultSet resultSet = preparedStatement.executeQuery();

            while (resultSet.next()) {
                String artistName = resultSet.getString("b.name");
                String artistUrl = resultSet.getString("b.url");
                int plays = resultSet.getInt("plays");
                long artist_id = resultSet.getInt("a.artist_id");

                ScrobbledArtist value = new ScrobbledArtist(null, artistName, plays);
                value.setUrl(artistUrl);
                value.setArtistId(artist_id);
                scrobbledArtists.add(value);
            }
        } catch (SQLException e) {
            throw new ChuuServiceException(e);
        }
        return scrobbledArtists;
    }

    @Override
    public Set<Pair<String, String>> getArtistBannedTags(Connection connection) {
        Set<Pair<String, String>> bannedTags = new HashSet<>();
        String queryString = "Select tag,name from banned_artist_tags a join artist b on a.artist_id = b.id";
        try (
                PreparedStatement preparedStatement = connection.prepareStatement(queryString)) {
            ResultSet resultSet = preparedStatement.executeQuery();

            while (resultSet.next()) {
                String tag = resultSet.getString(1);
                String artist = resultSet.getString(2);

                bannedTags.add(Pair.of(artist.toLowerCase(), tag.toLowerCase()));
            }
        } catch (SQLException e) {
            throw new ChuuServiceException(e);
        }
        return bannedTags;
    }

    @Override
    public List<String> getArtistTag(Connection connection, long artistId) {
        @Language("MariaDB") String queryString = "SELECT tag FROM artist_tags WHERE artist_id = ? ";
        List<String> returnList = new ArrayList<>();
        try (PreparedStatement preparedStatement = connection.prepareStatement(queryString)) {
            int i = 1;
            preparedStatement.setLong(i, artistId);
            ResultSet resultSet = preparedStatement.executeQuery();

            while (resultSet.next()) {
                String name = resultSet.getString("tag");
                returnList.add(name);
            }
            return returnList;

        } catch (SQLException e) {
            logger.warn(e.getMessage(), e);
            throw new ChuuServiceException(e);
        }

    }

    @Override
    public WrapperReturnNowPlaying globalWhoKnowsTrack(Connection connection, long trackId, int limit, long ownerId, boolean includeBotted) {

        @Language("MariaDB")
        String queryString =
                "SELECT a2.track_name,a3.name, a.lastfm_id, a.playNumber, coalesce(a2.url,a4.url,a3.url) as url, c.discord_id,c.privacy_mode " +
                        "FROM  scrobbled_track a" +
                        " JOIN track a2 ON a.track_id = a2.id  " +
                        " join artist a3 on a2.artist_id = a3.id " +
                        " left join album a4 on a2.album_id = a4.id " +
                        "JOIN `user` c on c.lastFm_Id = a.lastFM_ID " +
                        " where   (? or not c.botted_account or c.discord_id = ? )  " +
                        "and  a2.id = ? " +
                        "ORDER BY a.playNumber desc ";
        queryString = limit == Integer.MAX_VALUE ? queryString : queryString + "limit " + limit;
        try (PreparedStatement preparedStatement = connection.prepareStatement(queryString)) {

            int i = 1;
            preparedStatement.setBoolean(i++, includeBotted);
            preparedStatement.setLong(i++, ownerId);
            preparedStatement.setLong(i, trackId);


            ResultSet resultSet = preparedStatement.executeQuery();
            String url = "";
            String artistName = "";
            String trackName = "";
            List<ReturnNowPlaying> returnList = new ArrayList<>();

            int j = 0;
            while (resultSet.next() && (j < limit)) {
                url = resultSet.getString("url");
                artistName = resultSet.getString("a3.name");
                trackName = resultSet.getString("a2.track_name");

                String lastfmId = resultSet.getString("a.lastFM_ID");

                int playNumber = resultSet.getInt("a.playNumber");
                long discordId = resultSet.getLong("c.discord_ID");
                PrivacyMode privacyMode = PrivacyMode.valueOf(resultSet.getString("c.privacy_mode"));

                returnList.add(new GlobalReturnNowPlayingAlbum(discordId, lastfmId, artistName, playNumber, privacyMode, trackName));
            }
            /* Return booking. */
            return new WrapperReturnNowPlaying(returnList, returnList.size(), url, artistName + " - " + trackName);

        } catch (SQLException e) {
            throw new ChuuServiceException(e);
        }
    }

    @Override
    public UniqueWrapper<AlbumPlays> getUserTrackCrowns(Connection connection, String lastfmId, int crownthreshold, long guildId) {
        long discordId = -1L;
        @Language("MariaDB") String queryString = "SELECT d.name, a2.track_name, b.discord_id , playnumber AS orden" +
                " FROM  scrobbled_track a" +
                " JOIN user b ON a.lastfm_id = b.lastfm_id " +
                " JOIN track a2 ON a.track_id = a2.id " +
                " join artist d on a2.artist_id = d.id " +
                " WHERE  b.lastfm_id = ?" +
                " AND playnumber >= ? " +
                " AND  playnumber >= ALL" +
                "       (SELECT max(b.playnumber) " +
                " FROM " +
                "(SELECT in_a.track_id,in_a.playnumber" +
                " FROM scrobbled_track in_a  " +
                " JOIN " +
                " user in_b" +
                " ON in_a.lastfm_id = in_b.lastfm_id" +
                " NATURAL JOIN " +
                " user_guild in_c" +
                " WHERE guild_id = ?" +
                "   ) AS b" +
                "" +
                " WHERE b.track_id = a.track_id" +
                " GROUP BY track_id)" +
                " ORDER BY orden DESC";

        List<AlbumPlays> returnList = new ArrayList<>();
        try (PreparedStatement preparedStatement = connection.prepareStatement(queryString)) {
            int i = 1;
            preparedStatement.setString(i++, lastfmId);
            preparedStatement.setInt(i++, crownthreshold);
            preparedStatement.setLong(i, guildId);

            ResultSet resultSet = preparedStatement.executeQuery();


            while (resultSet.next()) {
                discordId = resultSet.getLong("b.discord_id");

                String artist = resultSet.getString("d.name");
                String album = resultSet.getString("a2.track_name");


                int plays = resultSet.getInt("orden");
                returnList.add(new AlbumPlays(artist, plays, album));
            }
        } catch (SQLException e) {
            logger.warn(e.getMessage(), e);
            throw new ChuuServiceException(e);
        }
        return new UniqueWrapper<>(returnList.size(), discordId, lastfmId, returnList);
    }

    @Override
    public List<LbEntry> trackCrownsLeaderboard(Connection connection, long guildId, int threshold) {
        @Language("MariaDB") String queryString = "SELECT t2.lastfm_id,t3.discord_id,count(t2.lastfm_id) ord " +
                "FROM " +
                "( " +
                "SELECT " +
                "        a.track_id,max(a.playnumber) plays " +
                "    FROM " +
                "         scrobbled_track a  " +
                "    JOIN " +
                "        user b  " +
                "            ON a.lastfm_id = b.lastfm_id  " +
                "    JOIN " +
                "        user_guild c  " +
                "            ON b.discord_id = c.discord_id  " +
                "    WHERE " +
                "        c.guild_id = ?  " +
                "    GROUP BY " +
                "        a.track_id  " +
                "  ) t " +
                "  JOIN scrobbled_track t2  " +
                "   " +
                "  ON t.plays = t2.playnumber AND t.track_id = t2.track_id " +
                "  JOIN user t3  ON t2.lastfm_id = t3.lastfm_id  " +
                "    JOIN " +
                "        user_guild t4  " +
                "            ON t3.discord_id = t4.discord_id  " +
                "    WHERE " +
                "        t4.guild_id = ?  ";
        if (threshold != 0) {
            queryString += " and t2.playnumber > ? ";
        }


        queryString += "  GROUP BY t2.lastfm_id,t3.discord_id " +
                "  ORDER BY ord DESC";


        return getLbEntries(connection, guildId, queryString, TrackCrownLbEntry::new, true, threshold);

    }

    @Override
    public ResultWrapper<UserArtistComparison> similarAlbumes(Connection connection, long artistId, List<String> lastFmNames, int limit) {
        String userA = lastFmNames.get(0);
        String userB = lastFmNames.get(1);

        @Language("MariaDB") String queryString =
                "SELECT c.album_name  , a.playnumber,b.playnumber ," +
                        "((a.playnumber + b.playnumber)/(abs(a.playnumber-b.playnumber)+1))  *" +
                        " (((a.playnumber + b.playnumber)) * 2.5) * " +
                        " IF((a.playnumber > 10 * b.playnumber OR b.playnumber > 10 * a.playnumber) AND LEAST(a.playnumber,b.playnumber) < 400 ,0.01,2) " +
                        "media ," +
                        " c.url " +
                        "FROM " +
                        "(SELECT album_id,playnumber " +
                        "FROM scrobbled_album " +
                        "JOIN user b ON scrobbled_album.lastfm_id = b.lastfm_id " +
                        "WHERE b.lastfm_id = ? and scrobbled_album.artist_id = ? ) a " +
                        "JOIN " +
                        "(SELECT album_id,playnumber " +
                        "FROM scrobbled_album " +
                        " JOIN user b ON scrobbled_album.lastfm_id = b.lastfm_id " +
                        " WHERE b.lastfm_id = ? and scrobbled_album.artist_id = ?) b " +
                        "ON a.album_id=b.album_id " +
                        "JOIN album c " +
                        "ON c.id=b.album_id" +
                        " ORDER BY media DESC";

        try (PreparedStatement preparedStatement = connection.prepareStatement(queryString)) {

            int i = 1;
            preparedStatement.setString(i++, userA);
            preparedStatement.setLong(i++, artistId);
            preparedStatement.setString(i++, userB);
            preparedStatement.setLong(i, artistId);


            /* Execute query. */
            ResultSet resultSet = preparedStatement.executeQuery();
            List<UserArtistComparison> returnList = new ArrayList<>();

            int counter = 0;
            while (resultSet.next()) {
                counter++;
                if (counter <= limit) {
                    String name = resultSet.getString("c.album_name");
                    int countA = resultSet.getInt("a.playNumber");
                    int countB = resultSet.getInt("b.playNumber");
                    String url = resultSet.getString("c.url");
                    returnList.add(new UserArtistComparison(countA, countB, name, userA, userB, url));
                }
            }

            return new ResultWrapper<>(counter, returnList);

        } catch (SQLException e) {
            throw new ChuuServiceException(e);
        }
    }

    @Override
    public ResultWrapper<UserArtistComparison> similarTracks(Connection connection, long artistId, List<String> lastFmNames, int limit) {
        String userA = lastFmNames.get(0);
        String userB = lastFmNames.get(1);

        @Language("MariaDB") String queryString =
                "SELECT c.track_name  , a.playnumber,b.playnumber ," +
                        "((a.playnumber + b.playnumber)/(abs(a.playnumber-b.playnumber)+1))  *" +
                        " (((a.playnumber + b.playnumber)) * 2.5) * " +
                        " IF((a.playnumber > 10 * b.playnumber OR b.playnumber > 10 * a.playnumber) AND LEAST(a.playnumber,b.playnumber) < 400 ,0.01,2) " +
                        "media ," +
                        " coalesce(c.url,d.url) as url " +
                        "FROM " +
                        "(SELECT track_id,playnumber " +
                        "FROM scrobbled_track " +
                        "JOIN user b ON scrobbled_track.lastfm_id = b.lastfm_id " +
                        "WHERE b.lastfm_id = ? and scrobbled_track.artist_id = ? ) a " +
                        "JOIN " +
                        "(SELECT track_id,playnumber " +
                        "FROM scrobbled_track " +
                        " JOIN user b ON scrobbled_track.lastfm_id = b.lastfm_id " +
                        " WHERE b.lastfm_id = ? and scrobbled_track.artist_id = ?) b " +
                        "ON a.track_id=b.track_id " +
                        "JOIN track c " +
                        "ON c.id=b.track_id " +
                        " left join album d on c.album_id =d.id " +
                        " ORDER BY media DESC";

        try (PreparedStatement preparedStatement = connection.prepareStatement(queryString)) {

            int i = 1;
            preparedStatement.setString(i++, userA);
            preparedStatement.setLong(i++, artistId);
            preparedStatement.setString(i++, userB);
            preparedStatement.setLong(i, artistId);


            /* Execute query. */
            ResultSet resultSet = preparedStatement.executeQuery();
            List<UserArtistComparison> returnList = new ArrayList<>();

            int counter = 0;
            while (resultSet.next()) {
                counter++;
                if (counter <= limit) {
                    String name = resultSet.getString("c.track_name");
                    int countA = resultSet.getInt("a.playNumber");
                    int countB = resultSet.getInt("b.playNumber");
                    String url = resultSet.getString("url");
                    returnList.add(new UserArtistComparison(countA, countB, name, userA, userB, url));
                }
            }

            return new ResultWrapper<>(counter, returnList);

        } catch (SQLException e) {
            throw new ChuuServiceException(e);
        }
    }

    @Override
    public ResultWrapper<UserArtistComparison> similarAlbumTracks(Connection connection, long albumId, List<String> lastFmNames, int limit) {
        String userA = lastFmNames.get(0);
        String userB = lastFmNames.get(1);

        @Language("MariaDB") String queryString =
                "SELECT c.track_name  , a.playnumber,b.playnumber ," +
                        "((a.playnumber + b.playnumber)/(abs(a.playnumber-b.playnumber)+1))  *" +
                        " (((a.playnumber + b.playnumber)) * 2.5) * " +
                        " IF((a.playnumber > 10 * b.playnumber OR b.playnumber > 10 * a.playnumber) AND LEAST(a.playnumber,b.playnumber) < 400 ,0.01,2) " +
                        "media ," +
                        " coalesce(c.url,d.url) as url " +
                        "FROM " +
                        "(SELECT track_id,playnumber " +
                        "FROM scrobbled_track a join track c on a.track_id = c.id " +
                        "JOIN user b ON a.lastfm_id = b.lastfm_id " +
                        "WHERE b.lastfm_id = ? and c.album_id = ? ) a " +
                        "JOIN " +
                        "(SELECT track_id,playnumber " +
                        "FROM scrobbled_track a join track c on a.track_id = c.id " +
                        " JOIN user b ON a.lastfm_id = b.lastfm_id " +
                        " WHERE b.lastfm_id = ? and c.album_id = ?) b " +
                        "ON a.track_id=b.track_id " +
                        "JOIN track c " +
                        "ON c.id=b.track_id " +
                        " left join album d on c.album_id =d.id " +
                        " ORDER BY media DESC";

        try (PreparedStatement preparedStatement = connection.prepareStatement(queryString)) {

            int i = 1;
            preparedStatement.setString(i++, userA);
            preparedStatement.setLong(i++, albumId);
            preparedStatement.setString(i++, userB);
            preparedStatement.setLong(i, albumId);


            /* Execute query. */
            ResultSet resultSet = preparedStatement.executeQuery();
            List<UserArtistComparison> returnList = new ArrayList<>();

            int counter = 0;
            while (resultSet.next()) {
                counter++;
                if (counter <= limit) {
                    String name = resultSet.getString("c.track_name");
                    int countA = resultSet.getInt("a.playNumber");
                    int countB = resultSet.getInt("b.playNumber");
                    String url = resultSet.getString("url");
                    returnList.add(new UserArtistComparison(countA, countB, name, userA, userB, url));
                }
            }

            return new ResultWrapper<>(counter, returnList);

        } catch (SQLException e) {
            throw new ChuuServiceException(e);
        }
    }

    @Override
    public UniqueWrapper<ArtistPlays> getGlobalAlbumCrowns(Connection connection, String lastfmid, int threshold, boolean includeBottedUsers, long ownerId) {
        List<ArtistPlays> returnList = new ArrayList<>();
        long discordId = 0;

        @Language("MariaDB") String queryString = "SELECT d.name,c.album_name , b.discord_id , playnumber AS orden" +
                " FROM  scrobbled_album  a" +
                " JOIN user b ON a.lastfm_id = b.lastfm_id" +
                " JOIN album c ON " +
                " a.album_id = c.id" +
                " join artist d on c.artist_id = d.id " +
                " WHERE  a.lastfm_id = ?" +
                " AND playnumber >= ?" +
                " AND  playnumber >= ALL" +
                "       (SELECT max(b.playnumber) " +
                " FROM " +
                "(SELECT in_a.album_id,in_a.playnumber" +
                " FROM scrobbled_album in_a  " +
                " JOIN " +
                " user in_b" +
                " ON in_a.lastfm_id = in_b.lastfm_id" +
                " where ? or not in_b.botted_account " +
                "   ) AS b" +
                " WHERE b.album_id = a.album_id" +
                " GROUP BY album_id)" +
                " ORDER BY orden DESC";

        try (PreparedStatement preparedStatement = connection.prepareStatement(queryString)) {
            preparedStatement.setString(1, lastfmid);
            preparedStatement.setInt(2, threshold);
            preparedStatement.setBoolean(3, includeBottedUsers);
            //preparedStatement.setLong(4, ownerId);


            ResultSet resultSet = preparedStatement.executeQuery();


            while (resultSet.next()) {

                String artist = resultSet.getString("d.name");
                String albumName = resultSet.getString("c.album_name");
                int plays = resultSet.getInt("orden");
                returnList.add(new AlbumPlays(artist, plays, albumName));
                // TODO
                discordId = resultSet.getLong("b.discord_id");

            }
        } catch (SQLException e) {
            logger.warn(e.getMessage(), e);
            throw new ChuuServiceException(e);
        }
        return new UniqueWrapper<>(returnList.size(), discordId, lastfmid, returnList);
    }

    @Override
    public UniqueWrapper<ArtistPlays> getGlobalTrackCrowns(Connection connection, String lastfmid, int threshold, boolean includeBottedUsers, long ownerId) {
        List<ArtistPlays> returnList = new ArrayList<>();
        long discordId = 0;

        @Language("MariaDB") String queryString = "SELECT d.name,c.track_name , b.discord_id , playnumber AS orden" +
                " FROM  scrobbled_track  a" +
                " JOIN user b ON a.lastfm_id = b.lastfm_id" +
                " JOIN track c ON " +
                " a.track_id = c.id" +
                " join artist d on c.artist_id = d.id " +
                " WHERE  a.lastfm_id = ?" +
                " AND playnumber >= ?" +
                " AND  playnumber >= ALL" +
                "       (SELECT max(b.playnumber) " +
                " FROM " +
                "(SELECT in_a.track_id,in_a.playnumber" +
                " FROM scrobbled_track in_a  " +
                " JOIN " +
                " user in_b" +
                " ON in_a.lastfm_id = in_b.lastfm_id" +
                " where ? or not in_b.botted_account " +
                "   ) AS b" +
                " WHERE b.track_id = a.track_id" +
                " GROUP BY track_id)" +
                " ORDER BY orden DESC";

        try (PreparedStatement preparedStatement = connection.prepareStatement(queryString)) {
            preparedStatement.setString(1, lastfmid);
            preparedStatement.setInt(2, threshold);
            preparedStatement.setBoolean(3, includeBottedUsers);
            //preparedStatement.setLong(4, ownerId);


            ResultSet resultSet = preparedStatement.executeQuery();


            while (resultSet.next()) {

                String artist = resultSet.getString("d.name");
                String albumName = resultSet.getString("c.track_name");
                int plays = resultSet.getInt("orden");
                returnList.add(new TrackPlays(artist, plays, albumName));
                // TODO
                discordId = resultSet.getLong("b.discord_id");

            }
        } catch (SQLException e) {
            logger.warn(e.getMessage(), e);
            throw new ChuuServiceException(e);
        }
        return new UniqueWrapper<>(returnList.size(), discordId, lastfmid, returnList);


    }

    @Override
    public UniqueWrapper<TrackPlays> getArtistGlobalTrackCrowns(Connection connection, String lastfmid, long artistId, int threshold, boolean includeBottedUsers) {
        List<TrackPlays> returnList = new ArrayList<>();
        long discordId = 0;

        @Language("MariaDB") String queryString = "SELECT d.name,c.track_name , b.discord_id , playnumber AS orden" +
                " FROM  scrobbled_track  a" +
                " JOIN user b ON a.lastfm_id = b.lastfm_id" +
                " JOIN track c ON " +
                " a.track_id = c.id" +
                " join artist d on c.artist_id = d.id " +
                " WHERE  a.lastfm_id = ?" +
                " and d.id = ?  " +

                " AND playnumber >= ?" +
                " AND  playnumber >= ALL" +
                "       (SELECT max(b.playnumber) " +
                " FROM " +
                "(SELECT in_a.track_id,in_a.playnumber" +
                " FROM scrobbled_track in_a  " +
                " JOIN " +
                " user in_b" +
                " ON in_a.lastfm_id = in_b.lastfm_id" +
                " join track in_c on in_a.track_id = in_c.id " +
                " where ? or not in_b.botted_account  " +
                " and in_c.artist_id = ? " +
                "   ) AS b" +
                " WHERE b.track_id = a.track_id" +
                " GROUP BY track_id)" +
                " ORDER BY orden DESC";

        try (PreparedStatement preparedStatement = connection.prepareStatement(queryString)) {
            preparedStatement.setString(1, lastfmid);
            preparedStatement.setLong(2, artistId);
            preparedStatement.setInt(3, threshold);
            preparedStatement.setBoolean(4, includeBottedUsers);
            preparedStatement.setLong(5, artistId);
            //preparedStatement.setLong(4, ownerId);


            ResultSet resultSet = preparedStatement.executeQuery();


            while (resultSet.next()) {

                String artist = resultSet.getString("d.name");
                String albumName = resultSet.getString("c.track_name");
                int plays = resultSet.getInt("orden");
                returnList.add(new TrackPlays(artist, plays, albumName));
                // TODO
                discordId = resultSet.getLong("b.discord_id");

            }
        } catch (SQLException e) {
            logger.warn(e.getMessage(), e);
            throw new ChuuServiceException(e);
        }
        return new UniqueWrapper<>(returnList.size(), discordId, lastfmid, returnList);

    }

    @Override
    public UniqueWrapper<TrackPlays> getUserArtistTrackCrowns(Connection connection, String lastfmId, int crownthreshold, long guildId, long artistId) {
        long discordId = -1L;
        @Language("MariaDB") String queryString = "SELECT  a2.track_name, b.discord_id , playnumber AS orden" +
                " FROM  scrobbled_track a" +
                " JOIN user b ON a.lastfm_id = b.lastfm_id " +
                " JOIN track a2 ON a.track_id = a2.id " +
                " WHERE  b.lastfm_id = ? and a2.artist_id = ? " +
                " AND playnumber >= ? " +
                " AND  playnumber >= ALL" +
                "       (SELECT max(b.playnumber) " +
                " FROM " +
                "(SELECT in_a.track_id,in_a.playnumber" +
                " FROM scrobbled_track in_a  " +
                " JOIN " +
                " user in_b " +
                " ON in_a.lastfm_id = in_b.lastfm_id " +
                " NATURAL JOIN " +
                " user_guild in_c " +
                " join track in_d on in_a.track_id = in_d.id " +
                " WHERE guild_id = ? and in_d.artist_id = ? " +
                "   ) AS b" +
                "" +
                " WHERE b.track_id = a.track_id" +
                " GROUP BY track_id)" +
                " ORDER BY orden DESC";

        List<TrackPlays> returnList = new ArrayList<>();
        try (PreparedStatement preparedStatement = connection.prepareStatement(queryString)) {
            int i = 1;
            preparedStatement.setString(i++, lastfmId);
            preparedStatement.setLong(i++, artistId);

            preparedStatement.setInt(i++, crownthreshold);
            preparedStatement.setLong(i++, guildId);
            preparedStatement.setLong(i, artistId);

            ResultSet resultSet = preparedStatement.executeQuery();


            while (resultSet.next()) {
                discordId = resultSet.getLong("b.discord_id");

                String album = resultSet.getString("a2.track_name");


                int plays = resultSet.getInt("orden");
                returnList.add(new TrackPlays(null, plays, album));
            }
        } catch (SQLException e) {
            logger.warn(e.getMessage(), e);
            throw new ChuuServiceException(e);
        }
        return new UniqueWrapper<>(returnList.size(), discordId, lastfmId, returnList);
    }

    @Override
    public Optional<String> findArtistUrlAbove(Connection connection, long artistId, int upvotes) {
        Set<Pair<String, String>> bannedTags = new HashSet<>();
        String queryString = "Select url from alt_url a where artist_id = ? and score > ? order by rand()";
        try (
                PreparedStatement preparedStatement = connection.prepareStatement(queryString)) {
            preparedStatement.setLong(1, artistId);
            preparedStatement.setInt(2, upvotes);
            ResultSet resultSet = preparedStatement.executeQuery();

            if (resultSet.next()) {
                return Optional.of(resultSet.getString(1));
            }
        } catch (SQLException e) {
            throw new ChuuServiceException(e);
        }
        return Optional.empty();
    }

    @Override
    public Optional<Instant> getLastScrobbled(Connection connection, String lastfmId, long artistId, String song) {
        Set<Pair<String, String>> bannedTags = new HashSet<>();
        String queryString = "Select  timestamp  from user_billboard_data_scrobbles  where artist_id = ? and lastfm_id = ? and track_name = ?  order by timestamp  desc limit 1";
        try (
                PreparedStatement preparedStatement = connection.prepareStatement(queryString)) {
            preparedStatement.setLong(1, artistId);
            preparedStatement.setString(2, lastfmId);
            preparedStatement.setString(3, song);
            ResultSet resultSet = preparedStatement.executeQuery();

            if (resultSet.next()) {
                return Optional.ofNullable(resultSet.getTimestamp(1).toInstant());
            }
        } catch (SQLException e) {
            throw new ChuuServiceException(e);
        }
        return Optional.empty();


    }

    @Override
    public Optional<Instant> getLastScrobbledArtist(Connection connection, String lastfmId, long artistId) {
        Set<Pair<String, String>> bannedTags = new HashSet<>();
        String queryString = "Select  timestamp  from user_billboard_data_scrobbles  where artist_id = ? and lastfm_id = ?   order by timestamp  desc limit 1";
        try (
                PreparedStatement preparedStatement = connection.prepareStatement(queryString)) {
            preparedStatement.setLong(1, artistId);
            preparedStatement.setString(2, lastfmId);
            ResultSet resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) {
                return Optional.ofNullable(resultSet.getTimestamp(1).toInstant());
            }
        } catch (SQLException e) {
            throw new ChuuServiceException(e);
        }
        return Optional.empty();
    }

    @Override
    public int getCurrentCombo(Connection connection, String lastfm_id, long artistId) {

        @Language("MariaDB") String queryString = """
                SELECT count(*)
                 FROM user_billboard_data_scrobbles a where `timestamp` > (select  max(`timestamp`) from user_billboard_data_scrobbles where artist_id != ?  && lastfm_id = ?  )  and lastfm_id = ?     
                """;


        try (PreparedStatement preparedStatement = connection.prepareStatement(queryString)) {
            int i = 1;
            preparedStatement.setLong(i++, artistId);
            preparedStatement.setString(i++, lastfm_id);
            preparedStatement.setString(i, lastfm_id);

            /* Execute query. */
            ResultSet resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) {
                return resultSet.getInt(1);
            }
            return 0;

        } catch (SQLException e) {
            throw new ChuuServiceException(e);
        }


    }


    private void getScoredAlbums(List<ScoredAlbumRatings> returnList, ResultSet resultSet) throws SQLException {

        while (resultSet.next()) {
            long count = resultSet.getLong(1);
            double average = resultSet.getDouble(2);
            String url = resultSet.getString(3);
            returnList.add(new ScoredAlbumRatings(0, "", url, count, average, ""));

        }
    }

}





