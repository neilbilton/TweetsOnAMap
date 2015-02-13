package com.example.tweetsonamap;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

public class Tweet {

	public String id;
	public LatLng position;
	public Long expires;
	public String username;
	public String status;
	public boolean isPending;
	public Marker pin;
	
	Tweet(
			Long id, 
			Double latitude, 
			Double longitude, 
			String username,
			String status
			)
	{
		this.id = "" + id;
		this.position = new LatLng(latitude, longitude);
		this.expires = System.currentTimeMillis() + Constants.TWEET_EXPIRE_MINUTES*(1000*60);
		this.username = username;
		this.status = status;
		this.isPending = true;

	}

	Tweet(String message)
	{
		String tweet = message;
		String[] tweetSplit = tweet.split("¬");

		id = tweetSplit[0];
		expires = Long.parseLong(tweetSplit[1]);

		Double latitude = Double.parseDouble(tweetSplit[2]);
		Double longitude = Double.parseDouble(tweetSplit[3]);
		position = new LatLng(latitude, longitude);

		username = tweetSplit[4];
		status = tweetSplit[5];
		isPending = false;
		
	}

	public void createPinOnMap(GoogleMap map) {
		
				MarkerOptions mapTweet = new MarkerOptions();

				mapTweet.position(position);
				mapTweet.title(username);
				mapTweet.snippet(status);

				pin = map.addMarker(mapTweet);
		
	}
	
	public String getConcatenatedString()
	{
		String tweetString = id + "¬" + expires + "¬" + 
				position.latitude + "¬" + position.longitude + "¬" + 
				username + "¬" + status;

		return tweetString;
	}


}
