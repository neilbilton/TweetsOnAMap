package com.example.tweetsonamap;

import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.GoogleMap.InfoWindowAdapter;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import twitter4j.FilterQuery;
import twitter4j.GeoLocation;
import twitter4j.StallWarning;
import twitter4j.Status;
import twitter4j.StatusDeletionNotice;
import twitter4j.StatusListener;
import twitter4j.TwitterStream;
import twitter4j.TwitterStreamFactory;
import twitter4j.conf.ConfigurationBuilder;

import android.support.v4.app.FragmentActivity;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.TextView;

public class MainActivity extends FragmentActivity {

	private GoogleMap mMap;
	private Context mContext;
	
	// tweets are stored in shared prefs.
	private SharedPreferences mPrefs;
	private Editor mPrefsEditor;

	// uses twitter4j to listen for tweets on the stream
	TwitterStream mTwitterStream;
	StatusListener mTweetListener;
	
	@SuppressLint("UseSparseArrays") private Map<String, Tweet> mTweets = 
											new ConcurrentHashMap<String, Tweet>();

	// timer for checking for expired tweets
	Handler mHandler;
	Runnable mRunnable;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_map);

		mContext = getApplicationContext();
		
		mPrefs = getSharedPreferences(Constants.TWEET_PREFS, MODE_PRIVATE);
		mPrefsEditor = mPrefs.edit();

	}

	@Override
	protected void onResume() {
		// TODO Auto-generated method stub
		super.onResume();

		setUpMapIfNeeded();
		intialiseTweets();

		startTweetChecker();
		openTwitterStream();
	}

	@Override
	protected void onPause() {
		// TODO Auto-generated method stub
		super.onPause();

		if (mHandler != null) {
			mHandler.removeCallbacks(mRunnable);
			mHandler = null;
		}
		
		if (mMap != null) {
			mMap.clear();
			mMap = null;
		}
		
		if (mTwitterStream != null) {
			
			if (mTweetListener != null) {
				mTwitterStream.removeListener(mTweetListener);
				mTweetListener = null;
			}		
						
			mTwitterStream.cleanUp();
			mTwitterStream = null;
		}
	}

	// read the tweets currently saved in prefs and add markers to the map
	private void intialiseTweets() {
		
		if (mMap != null) {
			
			mMap.clear();
			Map<String, ?> keys = mPrefs.getAll();
		
			for (Map.Entry<String, ?> entry : keys.entrySet()) {

				Tweet tweet = new Tweet(entry.getValue().toString());

				MarkerOptions mapTweet = new MarkerOptions();
				mapTweet.position(tweet.position);
				mapTweet.title(tweet.username);
				mapTweet.snippet(tweet.status);
				
				tweet.createPinOnMap(mMap);				
				mTweets.put(tweet.id, tweet);
			}
		}

	}

	// check periodically if tweets have expired
	private void startTweetChecker() {
		if (mHandler == null) {

			mHandler = new Handler();
			mRunnable = new Runnable() {
				@Override
				public void run() {

					long currentMilli = System.currentTimeMillis();

					Iterator<Entry<String, Tweet>> it = mTweets.entrySet().iterator();
				    while (it.hasNext()) {
	
				    	final Map.Entry<String, Tweet> pairs = (Map.Entry<String, Tweet>)it.next();
				    	
				    	if (pairs.getValue().isPending) {
				    		
				    		pairs.getValue().isPending = false;
				    		
				    		// save to preferences
				    		mPrefsEditor.putString(pairs.getValue().id, 
				    							   pairs.getValue().getConcatenatedString());
				    		
				    		// add marker to map in UI thread
				    		runOnUiThread(new Runnable() {
				    			public void run() {
				    				pairs.getValue().createPinOnMap(mMap);
				    			}
				    		});
				    		
				    	}
				    	else if (pairs.getValue().expires < currentMilli) {
							
				    		// removed expired tweets from map and prefs
							mPrefsEditor.remove(pairs.getValue().id);
							pairs.getValue().pin.remove();
							mTweets.remove(pairs.getKey());
						}

				    }

				    // save changes to prefs
					mPrefsEditor.commit();

					//trigger timer
					mHandler.postDelayed(this, Constants.UPDATE_TWEETS_MILLISECONDS);
					
				}
			};

			mHandler.postDelayed(mRunnable, Constants.UPDATE_TWEETS_MILLISECONDS);
		}
	}

	// use twitter4j to open up a listener to the twitter stream
	private void openTwitterStream() {
		
		ConfigurationBuilder cb = new ConfigurationBuilder();
		cb.setDebugEnabled(false);
		cb.setOAuthConsumerKey(Constants.CONSUMER_KEY);
		cb.setOAuthConsumerSecret(Constants.CONSUMER_SECRET);
		cb.setOAuthAccessToken(Constants.ACCESS_TOKEN);
		cb.setOAuthAccessTokenSecret(Constants.ACCESS_TOKEN_SECRET);

		mTwitterStream = new TwitterStreamFactory(cb.build()).getInstance();
		mTweetListener = new StatusListener() {

			@Override
			public void onStatus(Status status) {

				final GeoLocation geoloc = status.getGeoLocation();

				// check if tweet has a location
				if (geoloc != null) {

					final String username = status.getUser().getScreenName();
					final String content = status.getText();

					final Tweet newTweet = new Tweet(status.getId(),
							geoloc.getLatitude(), geoloc.getLongitude(),
							username, content);
					
					// will be pending.
					// handler will save to prefs and draw to map
					mTweets.put(newTweet.id, newTweet);					
				}
			}

			@Override
			public void onException(Exception arg0) {
				// TODO Auto-generated method stub
			}

			@Override
			public void onDeletionNotice(StatusDeletionNotice arg0) {
				// TODO Auto-generated method stub
			}

			@Override
			public void onScrubGeo(long arg0, long arg1) {
				// TODO Auto-generated method stub
			}

			@Override
			public void onTrackLimitationNotice(int arg0) {
				// TODO Auto-generated method stub
			}

			@Override
			public void onStallWarning(StallWarning arg0) {
				// TODO Auto-generated method stub
			}

		};

		FilterQuery fq = new FilterQuery();
		String keywords[] = { Constants.TWITTER_TRACK };
		fq.track(keywords);

		mTwitterStream.addListener(mTweetListener);
		mTwitterStream.filter(fq);

	}

	private void setUpMapIfNeeded() {
		// Do a null check to confirm that we have not already instantiated the
		// map.
		if (mMap == null) {
			// Try to obtain the map from the SupportMapFragment.
			mMap = ((SupportMapFragment) getSupportFragmentManager()
					.findFragmentById(R.id.map)).getMap();
			// Check if we were successful in obtaining the map.
			if (mMap != null) {
				setUpPopups();
			}
		}
	}

	private void setUpPopups() {
		// Setting a custom info window adapter for the google map
		mMap.setInfoWindowAdapter(new InfoWindowAdapter() {

			// Use default InfoWindow frame
			@Override
			public View getInfoWindow(Marker arg0) {
				return null;
			}

			// Defines the contents of the InfoWindow
			@Override
			public View getInfoContents(Marker arg0) {

				// Getting view from the layout file info_window_layout
				View v = View.inflate(mContext, R.layout.info_window_layout, null);

				// following code splits the message onto 3 lines,
				// breaking on spaces after 50 chrs per line
				String wholeMessage = arg0.getSnippet();

				String message1 = wholeMessage;
				String message2 = "";
				String message3 = "";

				if (wholeMessage.length() > 50) {
					int lastIndex = 0;
					for (int i = 0; i < wholeMessage.length(); i++) {
						if ((wholeMessage.charAt(i) == ' ') && (i > 50)) {
							// gone too far
							for (int j = i - 1; j > 0; j--) {
								if (wholeMessage.charAt(j) == ' ') {
									message1 = wholeMessage.substring(0, j);
									lastIndex = j;
									break;
								}
							}
							break;
						}
					}
					for (int i = lastIndex; i < wholeMessage.length(); i++) {
						if ((wholeMessage.charAt(i) == ' ') && (i > 100)) {
							// gone too far
							for (int j = i - 1; j > 0; j--) {
								if (wholeMessage.charAt(j) == ' ') {
									message2 = wholeMessage.substring(
											lastIndex, j);
									lastIndex = j;
									break;
								}
							}
							break;
						}
					}

					if (message2.length() == 0)
						message2 = wholeMessage.substring(lastIndex,
								wholeMessage.length());
					else {
						message3 = wholeMessage.substring(lastIndex,
								wholeMessage.length());
					}
				}

				TextView title = (TextView) v.findViewById(R.id.username);
				title.setText(arg0.getTitle());

				TextView line_one = (TextView) v.findViewById(R.id.line_one);
				TextView line_two = (TextView) v.findViewById(R.id.line_two);
				TextView line_three = (TextView) v
						.findViewById(R.id.line_three);

				line_one.setText(message1);
				line_two.setText(message2);
				line_three.setText(message3);

				// Returning the view containing InfoWindow contents
				return v;

			}
		});
	}

}