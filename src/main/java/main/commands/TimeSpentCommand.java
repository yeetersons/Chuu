package main.commands;

import dao.DaoImplementation;
import dao.entities.SecondsTimeFrameCount;
import dao.entities.TimeFrameEnum;
import main.exceptions.LastFMNoPlaysException;
import main.exceptions.LastFmEntityNotFoundException;
import main.exceptions.LastFmException;
import main.parsers.TimerFrameParser;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.Collections;
import java.util.List;

public class TimeSpentCommand extends ConcurrentCommand {
	public TimeSpentCommand(DaoImplementation dao) {
		super(dao);
		this.parser = new TimerFrameParser(dao, TimeFrameEnum.WEEK);
	}

	@Override
	String getDescription() {
		return "Minutes listened last week";
	}

	@Override
	List<String> getAliases() {
		return Collections.singletonList("minutes");
	}

	@Override
	protected void onCommand(MessageReceivedEvent e) {
		String[] message;
		message = parser.parse(e);
		if (message == null)
			return;
		String username = message[0];
		long discordId = Long.parseLong(message[1]);
		String timeframe = message[2];
		String usableString = getUserStringConsideringGuildOrNot(e, discordId, username);
		if (!timeframe.equals("7day") && !timeframe.equals("1month") && !timeframe.equals("3month")) {
			sendMessageQueue(e, "Only [w]eek,[m]onth and [q]uarter are supported at the moment , sorry :'(");
			return;
		}
		try {
			SecondsTimeFrameCount wastedOnMusic = lastFM.getMinutesWastedOnMusic(username, timeframe);

			sendMessageQueue(e, "**" + usableString + "** played " +
					wastedOnMusic.getMinutes() +
					" minutes of music, " + String
					.format("(%d:%02d ", wastedOnMusic.getHours(),
							wastedOnMusic.getRemainingMinutes()) +
					CommandUtil.singlePlural(wastedOnMusic.getHours(), "hour", "hours") +
					"), listening to " + wastedOnMusic.getCount() + " different tracks in the last " +
					wastedOnMusic.getTimeFrame().toString()
							.toLowerCase());
		} catch (LastFMNoPlaysException ex) {
			parser.sendError(parser.getErrorMessage(3), e);

		} catch (LastFmEntityNotFoundException ex) {
			parser.sendError(parser.getErrorMessage(4), e);
		} catch (LastFmException ex) {
			parser.sendError(parser.getErrorMessage(2), e);
		}
	}

	@Override
	String getName() {
		return "Wasted On Music";
	}
}