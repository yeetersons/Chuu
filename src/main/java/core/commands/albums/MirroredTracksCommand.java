package core.commands.albums;

import core.commands.utils.CommandCategory;
import core.commands.utils.CommandUtil;
import core.exceptions.LastFmException;
import core.imagerenderer.TrackDistributor;
import core.parsers.ArtistAlbumParser;
import core.parsers.Parser;
import core.parsers.params.ArtistAlbumParameters;
import core.services.UserInfoService;
import core.services.tracklist.UserTrackListService;
import dao.ChuuService;
import dao.entities.*;
import dao.exceptions.InstanceNotFoundException;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class MirroredTracksCommand extends AlbumPlaysCommand {

    public MirroredTracksCommand(ChuuService dao) {

        super(dao);
    }


    @Override
    protected CommandCategory initCategory() {
        return CommandCategory.USER_STATS;
    }

    @Override
    public String getDescription() {
        return "Compare yourself with another user on one specific album";
    }

    @Override
    public Parser<ArtistAlbumParameters> initParser() {
        ArtistAlbumParser parser = new ArtistAlbumParser(getService(), lastFM);
        parser.setExpensiveSearch(true);
        return parser;
    }

    @Override
    public List<String> getAliases() {
        return Arrays.asList("comparetracks", "tracklistcompare", "tlc", "ctl");
    }

    @Override
    public String getName() {
        return "Track list comparison";
    }

    @Override
    protected void doSomethingWithAlbumArtist(ScrobbledArtist scrobbledArtist, String album, MessageReceivedEvent e, long who, ArtistAlbumParameters params) throws LastFmException, InstanceNotFoundException {
        User author = params.getE().getAuthor();
        LastFMData secondUser = params.getLastFMData();
        if (author.getIdLong() == secondUser.getDiscordId()) {
            sendMessageQueue(e, "You need to provide at least one other user (ping,discord id,tag format, u:username or lfm:lastfm_name )");
            return;
        }
        String artist = scrobbledArtist.getArtist();
        LastFMData ogData = getService().findLastFMData(author.getIdLong());

        ScrobbledAlbum scrobbledAlbum = CommandUtil.validateAlbum(getService(), scrobbledArtist.getArtistId(), album, lastFM);
        scrobbledAlbum.setArtist(scrobbledArtist.getArtist());

        Optional<FullAlbumEntity> trackList1 = new UserTrackListService(getService(), ogData.getName()).getTrackList(scrobbledAlbum, ogData, scrobbledArtist.getUrl());
        if (trackList1.isEmpty()) {
            sendMessageQueue(e, "Couldn't find a tracklist for " + CommandUtil.cleanMarkdownCharacter(scrobbledArtist.getArtist()
            ) + " - " + CommandUtil.cleanMarkdownCharacter(scrobbledAlbum.getAlbum()));
            return;

        }
        Optional<FullAlbumEntity> trackList2 = new UserTrackListService(getService(), secondUser.getName()).getTrackList(scrobbledAlbum, secondUser, scrobbledArtist.getUrl());
        if (trackList2.isEmpty()) {
            sendMessageQueue(e, "Couldn't find a tracklist for " + CommandUtil.cleanMarkdownCharacter(scrobbledArtist.getArtist()
            ) + " - " + CommandUtil.cleanMarkdownCharacter(scrobbledAlbum.getAlbum()));
            return;
        }
        UserInfoService userInfoService = new UserInfoService(getService());
        UserInfo userInfo = userInfoService.getUserInfo(ogData);
        userInfo.setUsername(CommandUtil.getUserInfoNotStripped(e, ogData.getDiscordId()).getUsername());
        UserInfo userInfo2 = userInfoService.getUserInfo(secondUser);
        userInfo2.setUsername(CommandUtil.getUserInfoNotStripped(e, secondUser.getDiscordId()).getUsername());
        BufferedImage bufferedImage = TrackDistributor.drawImageMirrored(trackList1.get(), trackList2.get(), userInfo, userInfo2);
        sendImage(bufferedImage, e);
    }
}
