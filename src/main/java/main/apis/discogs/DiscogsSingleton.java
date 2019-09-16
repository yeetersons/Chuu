package main.apis.discogs;

public class DiscogsSingleton {

	private static volatile DiscogsApi instance;
	private static String secret;
	private static String clientID;

	//Not pretty
	public DiscogsSingleton(String secret2, String key2) {
		secret = secret2;
		clientID = key2;

	}


	public static DiscogsApi getInstanceUsingDoubleLocking() {
		if (instance == null) {
			synchronized (main.apis.discogs.DiscogsSingleton.class) {
				if (instance == null) {
					instance = new DiscogsApi(secret, clientID);
				}
			}
		}
		return instance;
	}


}