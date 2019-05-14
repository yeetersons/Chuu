package main;

import DAO.DaoImplementation;
import main.APIs.Discogs.DiscogsApi;
import main.APIs.Spotify.Spotify;
import main.Commands.*;
import main.ScheduledTasks.ImageUpdaterThread;
import main.ScheduledTasks.UpdaterThread;
import net.dv8tion.jda.api.AccountType;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;

import javax.security.auth.login.LoginException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Chuu {


	void relaunchInUTF8() throws InterruptedException, UnsupportedEncodingException {
		System.out.println("BotLauncher: We are not running in UTF-8 mode! This is a problem!");
		System.out.println("BotLauncher: Relaunching in UTF-8 mode using -Dfile.encoding=UTF-8");

		String[] command = new String[]{"java", "-Dfile.encoding=UTF-8", "-jar", Main.getThisJarFile().getAbsolutePath()};

		//Relaunches the bot using UTF-8 mode.
		ProcessBuilder processBuilder = new ProcessBuilder(command);
		processBuilder.inheritIO(); //Tells the new process to use the same command line as this one.
		try {
			Process process = processBuilder.start();
			process.waitFor();  //We wait here until the actual bot stops. We do this so that we can keep using the same command line.
			System.exit(process.exitValue());
		} catch (IOException e) {
			if (e.getMessage().contains("\"java\"")) {
				System.out.println("BotLauncher: There was an error relaunching the bot. We couldn't find Java to launch with.");
				System.out.println("BotLauncher: Attempted to relaunch using the command:\n   " + String.join(" ", command));
				System.out.println("BotLauncher: Make sure that you have Java properly set in your Operating System's PATH variable.");
				System.out.println("BotLauncher: Stopping here.");
			} else {
				e.printStackTrace();
			}
		}
	}


	void setupBot() {
		Properties properties = readToken();
		JDABuilder builder = new JDABuilder(AccountType.BOT);

		DaoImplementation dao = new DaoImplementation();
		Spotify spotifyWrapper = new Spotify(properties.getProperty("client_ID"), properties.getProperty("client_Secret"));
		DiscogsApi discogsApi = new DiscogsApi();
		builder.setToken(properties.getProperty("DISCORD_TOKEN"));
		HelpCommand help = new HelpCommand();
		//Dao get all users discord ID and if someone not already -> erase
		// Add event listener for someone when he leaves -> erase

		builder.addEventListeners(help);
		AdministrativeCommand commandAdministrator = new AdministrativeCommand(dao);
		builder.addEventListeners(help.registerCommand(commandAdministrator));
		builder.addEventListeners(help.registerCommand(new NowPlayingCommand(dao)));
		builder.addEventListeners(help.registerCommand(new WhoKnowsCommand(dao, discogsApi)));
		builder.addEventListeners(help.registerCommand(new WhoKnowsNPCommand(dao, discogsApi)));
		builder.addEventListeners(help.registerCommand(new ChartCommand(dao)));
		builder.addEventListeners(help.registerCommand(new SetCommand(dao)));
		builder.addEventListeners(help.registerCommand(new AllPlayingCommand(dao)));
		builder.addEventListeners(help.registerCommand(new TasteCommand(dao)));
		builder.addEventListeners(help.registerCommand(new TopCommand(dao)));
		builder.addEventListeners(help.registerCommand(new UpdateCommand(dao)));
		builder.addEventListeners(help.registerCommand(new NPSpotifyCommand(dao, spotifyWrapper)));
		builder.addEventListeners(help.registerCommand(new UniqueCommand(dao)));
		builder.addEventListeners(help.registerCommand(new NPYoutubeCommand(dao)));
		builder.addEventListeners(help.registerCommand(new ArtistCommand(dao)));
		builder.addEventListeners(help.registerCommand(new AlbumSongPlaysCommand(dao)));
		builder.addEventListeners(help.registerCommand(new GuildTopCommand(dao)));


//		EventWaiter waiter = new EventWaiter(Executors.newSingleThreadScheduledExecutor(), false);
//		builder.addEventListener(waiter);
		builder.addEventListeners(help.registerCommand(new CrownsCommand(dao)));

		ScheduledExecutorService scheduledManager = Executors.newScheduledThreadPool(2);
		scheduledManager.scheduleAtFixedRate(new UpdaterThread(dao, null, true, discogsApi), 0, 2, TimeUnit.MINUTES);
		scheduledManager.scheduleAtFixedRate(new ImageUpdaterThread(dao), 0, 10, TimeUnit.MINUTES);

		try {
			JDA jda = builder.build().awaitReady();
			commandAdministrator.onStartup(jda);


		} catch (LoginException | InterruptedException e) {
			e.printStackTrace();

		}
	}

	private Properties readToken() {

		Properties properties = new Properties();

		try (InputStream in = Main.class.getResourceAsStream("/" + "all.properties")) {
			properties.load(in);

			return properties;

		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}