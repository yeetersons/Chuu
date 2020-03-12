package core.commands;

import core.apis.discogs.DiscogsApi;
import core.apis.discogs.DiscogsSingleton;
import core.apis.spotify.Spotify;
import core.apis.spotify.SpotifySingleton;
import core.exceptions.InstanceNotFoundException;
import core.exceptions.LastFmException;
import core.parsers.ArtistParser;
import dao.ChuuService;
import dao.entities.GlobalCrown;
import dao.entities.ScrobbledArtist;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class GlobalArtistCommand extends ConcurrentCommand {
    private final DiscogsApi discogsApi;
    private final Spotify spotify;

    public GlobalArtistCommand(ChuuService dao) {
        super(dao);
        this.discogsApi = DiscogsSingleton.getInstanceUsingDoubleLocking();
        this.spotify = SpotifySingleton.getInstanceUsingDoubleLocking();
        this.parser = new ArtistParser(dao, lastFM);
    }

    @Override
    public String getDescription() {
        return "Like who knows but for all bot members";
    }

    @Override
    public List<String> getAliases() {
        return Collections.singletonList("global");
    }

    @Override
    public String getName() {
        return "Global knows";
    }

    @Override
    void onCommand(MessageReceivedEvent e) throws LastFmException, InstanceNotFoundException {
        String[] returned;
        returned = parser.parse(e);
        if (returned == null)
            return;
        long userId = Long.parseLong(returned[1]);
        ScrobbledArtist validable = new ScrobbledArtist(returned[0], 0, "");
        CommandUtil.validate(getService(), validable, lastFM, discogsApi, spotify);
        List<GlobalCrown> globalArtistRanking = getService().getGlobalArtistRanking(validable.getArtistId());

        if (globalArtistRanking.isEmpty()) {
            sendMessageQueue(e, "No one knows " + validable.getArtist());
            return;
        }
        Optional<GlobalCrown> yourPosition = globalArtistRanking.stream().filter(x -> x.getDiscordId() == userId).findFirst();
        int totalPeople = globalArtistRanking.size();
        int totalPlays = globalArtistRanking.stream().mapToInt(GlobalCrown::getPlaycount).sum();
        MessageBuilder messageBuilder = new MessageBuilder();
        EmbedBuilder embedBuilder = new EmbedBuilder();

        if (yourPosition.isPresent()) {
            GlobalCrown globalCrown = yourPosition.get();
            int position = globalCrown.getRanking();

            embedBuilder.addField("Position:", position + "/" + totalPeople, true);
            //It means we have someone ahead of us
            if (position != 1) {
                embedBuilder.addField("Plays to rank up:", String.valueOf((globalArtistRanking.get(position - 2).getPlaycount() - globalCrown.getPlaycount())), true);
                if (position != 2) {
                    embedBuilder.addField("Plays for first position:", String.valueOf((globalArtistRanking.get(0).getPlaycount() - globalCrown.getPlaycount())), true);
                }
            }
            embedBuilder.addField("Your Plays:", String.valueOf(globalCrown.getPlaycount()), false);

        }
        embedBuilder.addField("Total Plays:", String.valueOf(totalPlays), false);
        embedBuilder.addField("Total Listeners:", String.valueOf(totalPeople), false);


        embedBuilder.setThumbnail(validable.getUrl().isEmpty() ? null : validable.getUrl());
        embedBuilder.setTitle("Who knows " + validable.getArtist() + " globally?");
        messageBuilder.setEmbed(embedBuilder.build()).sendTo(e.getChannel()).queue();

    }

}
