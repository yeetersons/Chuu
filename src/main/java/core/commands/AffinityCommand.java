package core.commands;

import core.exceptions.InstanceNotFoundException;
import core.exceptions.LastFmException;
import core.imagerenderer.LoveMaker;
import core.otherlisteners.Reactionary;
import core.parsers.AffinityParser;
import core.parsers.params.AffinityParameters;
import dao.ChuuService;
import dao.entities.Affinity;
import dao.entities.DiscordUserDisplay;
import dao.entities.LastFMData;
import dao.entities.UserInfo;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.awt.image.BufferedImage;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Credits: to lfmwhoknows bot owner for the idea
 */
public class AffinityCommand extends ConcurrentCommand {
    public static final int DEFAULT_THRESHOLD = 30;

    public AffinityCommand(ChuuService dao) {
        super(dao);
        this.parser = new AffinityParser(dao);
        this.respondInPrivate = false;
    }

    @Override
    public String getDescription() {
        return "Gets your affinity with a user or with the rest of the server";
    }

    @Override
    public List<String> getAliases() {
        return List.of("affinity", "aff", "soulmate");
    }

    @Override
    public String getName() {
        return "Affinity";
    }

    @Override
    void onCommand(MessageReceivedEvent e) throws LastFmException, InstanceNotFoundException {
        String[] parse = this.parser.parse(e);
        if (parse == null) {
            return;
        }
        AffinityParameters ap = new AffinityParameters(parse, e, DEFAULT_THRESHOLD);
        if (ap.isDoServer()) {
            doGuild(ap);
        } else {
            doIndividual(ap);
        }
    }

    private void doIndividual(AffinityParameters ap) throws LastFmException {
        MessageReceivedEvent e = ap.getE();

        Affinity affinity = getService().getAffinity(ap.getFirstLastfmId(), ap.getSecondLastfmId(), ap.getThreshold());
        DiscordUserDisplay first = CommandUtil.getUserInfoNotStripped(e, ap.getFirstDiscordID());
        DiscordUserDisplay second = CommandUtil.getUserInfoNotStripped(e, ap.getSecondDiscordID());
        List<UserInfo> userInfo = lastFM.getUserInfo(List.of(ap.getFirstLastfmId(), ap.getSecondLastfmId()));
        BufferedImage bufferedImage = LoveMaker.calculateLove(affinity, first, userInfo.get(0).getImage(), userInfo.get(1).getImage(), second);
        sendImage(bufferedImage, e);
    }

    private void doGuild(AffinityParameters ap) throws InstanceNotFoundException {
        MessageReceivedEvent e = ap.getE();
        LastFMData ogData = getService().findLastFMData(e.getAuthor().getIdLong());
        List<Affinity> serverAffinity = getService().getServerAffinity(ogData.getName(), e.getGuild().getIdLong(), ap.getThreshold());
        List<Affinity> collect = serverAffinity.stream().sorted(Comparator.comparing(Affinity::getAffinity).reversed()).collect(Collectors.toList());

        StringBuilder stringBuilder = new StringBuilder();
        List<String> string = collect.stream().map(x -> String.format(". [%s](%s) - %.2f%%%s matching%n", getUserString(e, x.getDiscordId()),
                CommandUtil.getLastFmUser(x.getReceivingLastFmId()),
                (x.getAffinity() > 1 ? 1 : x.getAffinity()) * 100, x.getAffinity() > 1 ? "+" : "")).collect(Collectors.toList());
        for (int i = 0, size = collect.size(); i < 10 && i < size; i++) {
            String text = string.get(i);
            stringBuilder.append(i + 1).append(text);
        }
        DiscordUserDisplay uinfo = CommandUtil.getUserInfoConsideringGuildOrNot(e, e.getAuthor().getIdLong());
        EmbedBuilder embedBuilder = new EmbedBuilder()
                .setDescription(stringBuilder)
                .setTitle(uinfo.getUsername() + "'s soulmates in " + CommandUtil.cleanMarkdownCharacter(e.getGuild().getName()))
                .setColor(CommandUtil.randomColor())
                .setFooter(String.format("%s's affinity using a threshold of %d plays!%n", CommandUtil.markdownLessString(uinfo.getUsername()), ap.getThreshold()), null)
                .setThumbnail(e.getGuild().getIconUrl());
        MessageBuilder mes = new MessageBuilder();
        e.getChannel().sendMessage(mes.setEmbed(embedBuilder.build()).build()).queue(message1 ->
                new Reactionary<>(collect, message1, embedBuilder));
    }

}