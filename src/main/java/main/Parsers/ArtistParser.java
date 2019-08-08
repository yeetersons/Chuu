package main.Parsers;

import DAO.DaoImplementation;
import DAO.Entities.NowPlayingArtist;
import main.APIs.last.ConcurrentLastFM;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.Arrays;

public class ArtistParser extends ArtistAlbumParser {

	public ArtistParser(DaoImplementation dao, ConcurrentLastFM lastFM) {
		super(dao, lastFM);
	}

	public ArtistParser(DaoImplementation dao, ConcurrentLastFM lastFM, OptionalEntity... strings) {
		super(dao, lastFM);
		opts.addAll(Arrays.asList(strings));
	}

	@Override
	public String[] doSomethingWithNp(NowPlayingArtist np, Member sample) {
		return new String[]{np.getArtistName(), String.valueOf(sample.getIdLong())};
	}

	@Override
	public String[] doSomethingWithString(String[] subMessage, Member sample, MessageReceivedEvent e) {

		return new String[]{artistMultipleWords(subMessage), String.valueOf(sample.getIdLong())};
	}

	@Override
	public String getUsageLogic(String commandName) {

		return "**" + commandName + " *artist*** \n";

	}


}
