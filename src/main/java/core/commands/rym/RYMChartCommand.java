package core.commands.rym;

import core.apis.last.entities.chartentities.RYMChartEntity;
import core.apis.last.entities.chartentities.UrlCapsule;
import core.commands.charts.ChartableCommand;
import core.commands.utils.CommandUtil;
import core.parsers.ChartableParser;
import core.parsers.OnlyChartSizeParser;
import core.parsers.OptionalEntity;
import core.parsers.params.ChartSizeParameters;
import dao.ChuuService;
import dao.entities.CountWrapper;
import dao.entities.DiscordUserDisplay;
import dao.entities.ScoredAlbumRatings;
import dao.entities.TimeFrameEnum;
import net.dv8tion.jda.api.EmbedBuilder;
import org.knowm.xchart.PieChart;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class RYMChartCommand extends ChartableCommand<ChartSizeParameters> {
    public RYMChartCommand(ChuuService dao) {
        super(dao);
    }

    @Override
    public ChartableParser<ChartSizeParameters> initParser() {
        OnlyChartSizeParser onlyChartSizeParser = new OnlyChartSizeParser(getService(), TimeFrameEnum.ALL);
        onlyChartSizeParser.addOptional(new OptionalEntity("global", " show ratings from all bot users instead of only from this server"));
        onlyChartSizeParser.addOptional(new OptionalEntity("server", " show ratings from users only in this server"));
        onlyChartSizeParser.addOptional(new OptionalEntity("usestars", "show stars instead of numbers on global and server chart"));

        onlyChartSizeParser.replaceOptional("plays", new OptionalEntity("noratings", "don't display ratings"));
        onlyChartSizeParser.addOptional(new OptionalEntity("plays", "shows this with ratings", true, "noratings"));
        return onlyChartSizeParser;
    }

    @Override
    public String getDescription() {
        return "Image of top rated albums for a user /server or for the bot";
    }

    @Override
    public List<String> getAliases() {
        return List.of("rymc", "rymchart");
    }

    @Override
    public String getName() {
        return "Rate Your Music Chart";
    }

    @Override
    public CountWrapper<BlockingQueue<UrlCapsule>> processQueue(ChartSizeParameters params) {
        List<ScoredAlbumRatings> selfRatingsScore;
        boolean server = params.hasOptional("server");
        boolean global = params.hasOptional("global");
        if (server && params.getE().isFromGuild()) {
            long idLong = params.getE().getGuild().getIdLong();
            selfRatingsScore = getService().getServerTopRatings(idLong);
        } else {
            if (global || (server && !params.getE().isFromGuild())) {
                selfRatingsScore = getService().getGlobalTopRatings();
            } else {
                selfRatingsScore = getService().getSelfRatingsScore(params.getLastFMData().getDiscordId(), null);
            }
        }
        AtomicInteger atomicInteger = new AtomicInteger(0);
        boolean isNoRatings = params.hasOptional("noratings");
        boolean isUseStars = params.hasOptional("usestars");


        LinkedBlockingDeque<UrlCapsule> chartRatings = selfRatingsScore.stream().map(x -> {
            boolean useAverage = false;
            double average = 0;
            int score = (int) x.getScore();
            if (server || global) {
                useAverage = !isUseStars;
                score = (int) Math.round(x.getAverage());
                average = x.getAverage();

            }
            RYMChartEntity rymChartEntity = new RYMChartEntity(x.getUrl(), atomicInteger.getAndIncrement(), x.getArtist(), x.getName(), params.isWriteTitles(), !isNoRatings, useAverage, average, x.getNumberOfRatings());
            rymChartEntity.setPlays(score);
            return rymChartEntity;
        }).limit(params.getX() * params.getY()).collect(Collectors.toCollection(LinkedBlockingDeque::new));// They in fact cannot be inferred you dumbass.
        return new CountWrapper<>(chartRatings.size(), chartRatings);
    }


    @Override
    public EmbedBuilder configEmbed(EmbedBuilder embedBuilder, ChartSizeParameters params, int count) {
        String title;
        String url;
        boolean isServer = params.hasOptional("server") && params.getE().isFromGuild();
        boolean isGlobal = !isServer && (params.hasOptional("global") || (params.hasOptional("server") && !params.getE().isFromGuild()));
        if (isServer) {
            title = "server";
            url = params.getE().getGuild().getIconUrl();
        } else if (isGlobal) {
            title = "bot";
            url = params.getE().getJDA().getSelfUser().getAvatarUrl();
        } else {
            Long discordId = params.getLastFMData().getDiscordId();
            DiscordUserDisplay userInfoConsideringGuildOrNot = CommandUtil.getUserInfoConsideringGuildOrNot(params.getE(), discordId);
            title = userInfoConsideringGuildOrNot.getUsername();
            url = userInfoConsideringGuildOrNot.getUrlImage();
        }
        String tile = "Top Rated albums in " + title;
        return embedBuilder.setAuthor(tile, url)
                .setThumbnail(url)
                .setFooter("Top " + params.getX() * params.getY() + " rated albums in " + tile)
                .setColor(CommandUtil.randomColor());

    }


    @Override
    public void noElementsMessage(ChartSizeParameters parameters) {


        sendMessageQueue(parameters.getE(), "Couldn't find any rating!");

    }


    @Override
    public String configPieChart(PieChart pieChart, ChartSizeParameters params, int count, String initTitle) {
        pieChart.setTitle("Top rated albums");
        return "";
    }


}
