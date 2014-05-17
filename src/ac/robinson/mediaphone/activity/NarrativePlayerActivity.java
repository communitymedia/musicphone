/*
 *  Copyright (C) 2012 Simon Robinson
 * 
 *  This file is part of Com-Me.
 * 
 *  Com-Me is free software; you can redistribute it and/or modify it 
 *  under the terms of the GNU Lesser General Public License as 
 *  published by the Free Software Foundation; either version 3 of the 
 *  License, or (at your option) any later version.
 *
 *  Com-Me is distributed in the hope that it will be useful, but WITHOUT 
 *  ANY WARRANTY; without even the implied warranty of MERCHANTABILITY 
 *  or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General 
 *  Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with Com-Me.
 *  If not, see <http://www.gnu.org/licenses/>.
 */

package ac.robinson.mediaphone.activity;

import java.io.File;
import java.io.FileInputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

import ac.robinson.mediaphone.MediaPhone;
import ac.robinson.mediaphone.MediaPhoneActivity;
import ac.robinson.musicphone.R;
import ac.robinson.mediaphone.addon.CustomMediaController;
import ac.robinson.mediaphone.provider.FrameItem;
import ac.robinson.mediaphone.provider.FramesManager;
import ac.robinson.mediaphone.provider.MediaPhoneProvider;
import ac.robinson.mediaphone.provider.NarrativeItem;
import ac.robinson.mediaphone.provider.NarrativesManager;
import ac.robinson.mediautilities.FrameMediaContainer;
import ac.robinson.util.BitmapUtilities;
import ac.robinson.util.DebugUtilities;
import ac.robinson.util.IOUtilities;
import ac.robinson.util.UIUtilities;
import ac.robinson.view.AutoResizeTextView;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.AssetFileDescriptor;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnErrorListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.media.SoundPool;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.FloatMath;
import android.util.Log;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.RelativeLayout;

import com.larvalabs.svgandroid.SVGParser;

//@Haiyue
//Implements OnTouchListener to enable touch events
public class NarrativePlayerActivity extends MediaPhoneActivity implements OnTouchListener {

	// @Haiyue
	// parameters for zoom in/out
	ImageView photoDisplay;
	// Matrices used to drag and zoom image
	Matrix matrix = new Matrix();
	Matrix savedMatrix = new Matrix();
	// Three states
	static final int NONE = 0;
	static final int DRAG = 1;
	static final int ZOOM = 2;
	int mode = NONE;
	// Points for zooming
	PointF start = new PointF();
	PointF mid = new PointF();
	float oldDist = 1f;
	int bmpWidth;
	int bmpHeight;

	private final int EXTRA_AUDIO_ITEMS = 2; // 3 audio items max, but only 2 for sound pool (other is in MediaPlayer)
	private SoundPool mSoundPool;
	private ArrayList<Integer> mFrameSounds;
	private int mNumExtraSounds;
	private boolean mMediaPlayerPrepared;
	private boolean mSoundPoolPrepared;
	private AssetFileDescriptor mSilenceFileDescriptor = null;
	private AssetFileDescriptor mSilenceFileDescriptor2 = null;
	private AssetFileDescriptor mSilenceFileDescriptor3 = null;
	private boolean mSilenceFilePlaying;
	private long mPlaybackStartTime;
	private long mPlaybackPauseTime;

	private MediaPlayer mMediaPlayer;
	private MediaPlayer mMediaPlayer2;
	private MediaPlayer mMediaPlayer3;
	private boolean mMediaPlayerError;
	private boolean mHasPlayed;
	private boolean mIsLoading;
	private TouchCallbackCustomMediaController mMediaController;
	private ArrayList<FrameMediaContainer> mNarrativeContentList;
	private int mNarrativeDuration;
	private int mPlaybackPosition;
	private int mInitialPlaybackOffset;
	private int mNonAudioOffset;
	private FrameMediaContainer mCurrentFrameContainer;
	private Bitmap mAudioPictureBitmap = null;

	private boolean mShowBackButton = false; // loaded from preferences on startup

	private int cVolume1, cVolume2, cVolume3, fVolume1, fVolume2, fVolume3;
	private float volume1, volume2, volume3;
	private int mediaIndex, mediaVolume;
	private boolean mPlayFromFrameEditor = false;
	private boolean mHasImage = false;

	private String currentAudioItem = null;
	private String currentAudioItem2 = null;
	private String currentAudioItem3 = null;
	private boolean mSilencePlay = false;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		UIUtilities.configureActionBar(this, true, true, R.string.title_playback, 0);
		UIUtilities.actionBarVisibility(this, false);
		setContentView(R.layout.narrative_player);

		// so that the volume controls always control media volume (rather than ringtone etc.)
		setVolumeControlStream(AudioManager.STREAM_MUSIC);

		mIsLoading = false;
		mMediaPlayerError = false;

		// load previous state on screen rotation
		mHasPlayed = false; // will begin playing if not playing already; used to stop unplayable narratives
		mPlaybackPosition = -1;
		if (savedInstanceState != null) {
			// mIsPlaying = savedInstanceState.getBoolean(getString(R.string.extra_is_playing));
			mPlaybackPosition = savedInstanceState.getInt(getString(R.string.extra_playback_position));
			mInitialPlaybackOffset = savedInstanceState.getInt(getString(R.string.extra_playback_offset));
			mNonAudioOffset = savedInstanceState.getInt(getString(R.string.extra_playback_non_audio_offset));
		} else {
			UIUtilities.setFullScreen(getWindow()); // start in full screen so initial playback bar hiding is smoother
		}
	}

	@Override
	public void onSaveInstanceState(Bundle savedInstanceState) {
		// savedInstanceState.putBoolean(getString(R.string.extra_is_playing), mIsPlaying);
		savedInstanceState.putInt(getString(R.string.extra_playback_position), mPlaybackPosition);
		savedInstanceState.putInt(getString(R.string.extra_playback_offset),
				mMediaPlayerController.getCurrentPosition() - mPlaybackPosition);
		savedInstanceState.putInt(getString(R.string.extra_playback_non_audio_offset), mNonAudioOffset);
		super.onSaveInstanceState(savedInstanceState);
	}

	@Override
	public void onWindowFocusChanged(boolean hasFocus) {
		super.onWindowFocusChanged(hasFocus);
		if (hasFocus) {
			if (!mHasPlayed) {
				preparePlayback();
			} else {
				if (mMediaPlayer != null && mMediaPlayer.isPlaying()) { // don't hide the controller if we're paused
					showMediaController(CustomMediaController.DEFAULT_VISIBILITY_TIMEOUT);
				}
			}
		} else {
			showMediaController(-1); // so if we're interacting with an overlay we don't constantly hide/show
			UIUtilities.setNonFullScreen(getWindow()); // so we don't have to wait for the playback bar to hide before
														// showing the notification bar
		}
	}

	@Override
	protected void onPause() {
		super.onPause();
		pauseMediaController();
	}

	@Override
	protected void onDestroy() {
		releasePlayer();
		super.onDestroy();
	}

	@Override
	public void onBackPressed() {
		if (mCurrentFrameContainer != null) {
			NarrativeItem deletedNarrative = NarrativesManager.findNarrativeByInternalId(getContentResolver(),
					mCurrentFrameContainer.mParentId);
			if (deletedNarrative != null && deletedNarrative.getDeleted()) {
				setResult(R.id.result_narrative_deleted_exit);
			} else {
				saveLastEditedFrame(mCurrentFrameContainer.mFrameId);
			}
		}
		mPlayFromFrameEditor = false;
		super.onBackPressed();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// TODO: if we couldn't open a temporary directory then exporting won't work
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.export_narrative, menu);
		inflater.inflate(R.menu.make_template, menu);
		inflater.inflate(R.menu.delete_narrative, menu);
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		pauseMediaController();
		final int itemId = item.getItemId();
		switch (itemId) {
			case R.id.menu_make_template:
				FrameItem templateFrame = FramesManager.findFrameByInternalId(getContentResolver(),
						mCurrentFrameContainer.mFrameId);
				runQueuedBackgroundTask(getNarrativeTemplateRunnable(templateFrame.getParentId(),
						MediaPhoneProvider.getNewInternalId(), true)); // don't need the id
				// TODO: do we need to keep the screen on?
				return true;

			case R.id.menu_delete_narrative:
				deleteNarrativeDialog(mCurrentFrameContainer.mFrameId);
				return true;

			case R.id.menu_export_narrative:
				exportNarrative();
				return true;

			default:
				return super.onOptionsItemSelected(item);
		}
	}

	@Override
	protected void loadPreferences(SharedPreferences mediaPhoneSettings) {
		// no normal preferences apply to this activity
	}

	@Override
	protected void configureInterfacePreferences(SharedPreferences mediaPhoneSettings) {
		// the soft back button (necessary in some circumstances)
		mShowBackButton = Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB
				&& mediaPhoneSettings.getBoolean(getString(R.string.key_show_back_button),
						getResources().getBoolean(R.bool.default_show_back_button));
		setMediaControllerListeners(); // TODO: do we need to do this here?
	}

	private void preparePlayback() {
		if (mNarrativeContentList != null && mNarrativeContentList.size() > 0 && mMediaPlayer != null // && mSoundPool
																										// != null
				&& mMediaController != null && mPlaybackPosition >= 0) {
			return; // no need to re-initialise
		}
		// need the parent id
		final Intent intent = getIntent();
		if (intent == null) {
			UIUtilities.showToast(NarrativePlayerActivity.this, R.string.error_loading_narrative_player);
			onBackPressed();
			return;
		}
		// @Haiyue
		// (Play narrative from frame editor)
		// pass volume information for each recorded sound track
		fVolume1 = intent.getIntExtra("Current Volume1 From FrameEditor", -1);
		fVolume2 = intent.getIntExtra("Current Volume2 From FrameEditor", -1);
		fVolume3 = intent.getIntExtra("Current Volume3 From FrameEditor", -1);
		mPlayFromFrameEditor = intent.getBooleanExtra("Play from editor", false);
		String startFrameId = intent.getStringExtra(getString(R.string.extra_internal_id));

		// TODO: lazily load (either via AsyncTask/Thread, or ImageCache for low-quality versions, later replaced)
		ContentResolver contentResolver = getContentResolver();
		FrameItem currentFrame = FramesManager.findFrameByInternalId(contentResolver, startFrameId);

		NarrativeItem currentNarrative = NarrativesManager.findNarrativeByInternalId(contentResolver,
				currentFrame.getParentId());
		mNarrativeContentList = currentNarrative.getContentList(contentResolver);

		// first launch
		boolean updatePosition = mPlaybackPosition < 0;
		if (updatePosition) {
			mInitialPlaybackOffset = 0;
			mNonAudioOffset = 0;
		}
		mNarrativeDuration = 0;
		for (FrameMediaContainer container : mNarrativeContentList) {
			if (updatePosition && startFrameId.equals(container.mFrameId)) {
				updatePosition = false;
				mPlaybackPosition = mNarrativeDuration;
			}
			mNarrativeDuration += container.mFrameMaxDuration;
		}
		if (mPlaybackPosition < 0) {
			mPlaybackPosition = 0;
			mInitialPlaybackOffset = 0;
			mNonAudioOffset = 0;
		}

		mCurrentFrameContainer = getMediaContainer(mPlaybackPosition, true);

		releasePlayer();
		mMediaPlayer = new MediaPlayer();
		mSoundPool = new SoundPool(EXTRA_AUDIO_ITEMS, AudioManager.STREAM_MUSIC, 100);
		mFrameSounds = new ArrayList<Integer>();

		mMediaPlayer2 = new MediaPlayer();
		mMediaPlayer3 = new MediaPlayer();

		// @Haiyue
		// Enable Touch
		photoDisplay = (ImageView) findViewById(R.id.image_playback);
		photoDisplay.setOnTouchListener(this);

		mMediaController = new TouchCallbackCustomMediaController(this);
		setMediaControllerListeners();

		RelativeLayout parentLayout = (RelativeLayout) findViewById(R.id.narrative_playback_container);
		RelativeLayout.LayoutParams controllerLayout = new RelativeLayout.LayoutParams(
				RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
		controllerLayout.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
		controllerLayout.setMargins(0, 0, 0, getResources().getDimensionPixelSize(R.dimen.button_padding));
		parentLayout.addView(mMediaController, controllerLayout);
		mMediaController.setAnchorView(findViewById(R.id.image_playback));
		showMediaController(CustomMediaController.DEFAULT_VISIBILITY_TIMEOUT); // (can use 0 for permanent visibility)
		mHasPlayed = true;
		prepareMediaItems(mCurrentFrameContainer);
	}

	public void handleButtonClicks(View currentButton) {
		if (!verifyButtonClick(currentButton)) {
			return;
		}

		showMediaController(CustomMediaController.DEFAULT_VISIBILITY_TIMEOUT);
		UIUtilities.setNonFullScreen(getWindow()); // so we don't have to wait for the playback bar to hide before
													// showing the notification bar
	}

	private void showMediaController(int timeout) {
		if (mMediaController != null) {
			if (!mMediaController.isShowing() || timeout <= 0) {
				mMediaController.show(timeout);
			} else {
				mMediaController.refreshShowTimeout();
			}
		}
	}

	private void makeMediaItemsVisible(boolean mediaControllerIsShowing) {
		// make sure the text view is visible above the playback bar
		Resources res = getResources();
		int mediaControllerHeight = res.getDimensionPixelSize(R.dimen.media_controller_height);
		if (mCurrentFrameContainer != null && mCurrentFrameContainer.mImagePath != null) {
			AutoResizeTextView textView = (AutoResizeTextView) findViewById(R.id.text_playback);
			RelativeLayout.LayoutParams textLayout = (RelativeLayout.LayoutParams) textView.getLayoutParams();
			int textPadding = res.getDimensionPixelSize(R.dimen.playback_text_padding);
			textLayout.setMargins(0, 0, 0, (mediaControllerIsShowing ? mediaControllerHeight : textPadding));
			textView.setLayoutParams(textLayout);
		}
	}

	private void pauseMediaController() {
		mMediaPlayerController.pause();
		showMediaController(-1); // to keep on showing until done here
		UIUtilities.releaseKeepScreenOn(getWindow());
	}

	private void setMediaControllerListeners() {
		if (mMediaController != null) {
			mMediaController.setPrevNextListeners(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					pauseMediaController();
					exportNarrative();
				}
			}, mShowBackButton ? new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					onBackPressed();
				}
			} : null, new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					pauseMediaController();
					// @Haiyue
					// print using google cloud printer
					if (mHasImage)
						printThis();
					else if (!mHasImage)
						UIUtilities.showToast(NarrativePlayerActivity.this, R.string.error_no_image_to_print);
				}
			});
		}
	}

	// @Haiyue
	// print image via google cloud printer
	private void printThis() {
		Intent printIntent = new Intent(this, PrintDialogActivity.class);
		printIntent.setDataAndType(savePrintedFile(mCurrentFrameContainer.mImagePath), "application/pdf");
		String timeStamp = new SimpleDateFormat("yyMMdd_HHmm").format(new Date());
		printIntent.putExtra("title", "Com Note " + timeStamp);
		startActivity(printIntent);
	}

	private Uri savePrintedFile(String path) {
		File mediaFile;
		mediaFile = new File(path);
		Uri uri = Uri.fromFile(mediaFile);
		return uri;
	}

	private void exportNarrative() {
		if (mCurrentFrameContainer != null) {
			FrameItem exportFrame = FramesManager.findFrameByInternalId(getContentResolver(),
					mCurrentFrameContainer.mFrameId);
			if (exportFrame != null) {
				exportContent(exportFrame.getParentId(), false);
			}
		}
	}

	private FrameMediaContainer getMediaContainer(int narrativePlaybackPosition, boolean updatePlaybackPosition) {
		mIsLoading = true;
		int currentPosition = 0;
		for (FrameMediaContainer container : mNarrativeContentList) {
			int newPosition = currentPosition + container.mFrameMaxDuration;
			if (narrativePlaybackPosition >= currentPosition && narrativePlaybackPosition < newPosition) {
				if (updatePlaybackPosition) {
					mPlaybackPosition = currentPosition;
				}
				return container;
			}
			currentPosition = newPosition;
		}
		return null;
	}

	private void prepareMediaItems(FrameMediaContainer container) {

		// load the audio for the media player
		Resources res = getResources();
		mSoundPoolPrepared = false;
		mMediaPlayerPrepared = false;
		mMediaPlayerError = false;
		mNonAudioOffset = 0;
		// unloadSoundPool();

		// @Haiyue
		// (Play narrative from narrative browser)
		// Pass volume information of recorded sounds corresponding to allocated frame internal id
		SharedPreferences outputPrefs = getSharedPreferences(container.mFrameId, 0);
		cVolume1 = outputPrefs.getInt("volume1", -1);
		cVolume2 = outputPrefs.getInt("volume2", -1);
		cVolume3 = outputPrefs.getInt("volume3", -1);

		if (cVolume1 == -1)
			cVolume1 = 0;
		if (cVolume2 == -1)
			cVolume2 = 0;
		if (cVolume3 == -1)
			cVolume3 = 0;

		mSoundPool.setOnLoadCompleteListener(mSoundPoolLoadListener);
		mNumExtraSounds = 0;

		boolean soundPoolAllowed = !DebugUtilities.hasSoundPoolBug();

		for (int i = 0, n = container.mAudioDurations.size(); i < n; i++) {
			if (container.mAudioDurations.get(i).intValue() == container.mFrameMaxDuration) {
				// @Haiyue
				// remember which sound has longest duration for future playback
				currentAudioItem = container.mAudioPaths.get(i);
				mediaIndex = i;
			} else {
				// playing *anything* in SoundPool at the same time as MediaPlayer crashes on Galaxy Tab
				if (soundPoolAllowed) {
					mSoundPool.load(container.mAudioPaths.get(i), 1);
					mNumExtraSounds += 1;
				}
			}
		}
		// @Haiyue
		// Allocate audio path for each recorded sound track
		if (mediaIndex == 0) {
			if (mNumExtraSounds == 2) {
				currentAudioItem2 = container.mAudioPaths.get(1);
				currentAudioItem3 = container.mAudioPaths.get(2);
			} else if (mNumExtraSounds == 1) {
				currentAudioItem2 = container.mAudioPaths.get(1);
				currentAudioItem3 = null;
			} else if (mNumExtraSounds == 0) {
				currentAudioItem2 = null;
				currentAudioItem3 = null;
			}
		} else if (mediaIndex == 1) {
			if (mNumExtraSounds == 2) {
				currentAudioItem2 = container.mAudioPaths.get(0);
				currentAudioItem3 = container.mAudioPaths.get(2);
			} else if (mNumExtraSounds == 1) {
				currentAudioItem2 = container.mAudioPaths.get(0);
				currentAudioItem3 = null;
			} else if (mNumExtraSounds == 0) {
				currentAudioItem2 = null;
				currentAudioItem3 = null;
			}
		} else if (mediaIndex == 2) {
			if (mNumExtraSounds == 2) {
				currentAudioItem2 = container.mAudioPaths.get(0);
				currentAudioItem3 = container.mAudioPaths.get(1);
			} else if (mNumExtraSounds == 1) {
				currentAudioItem2 = container.mAudioPaths.get(0);
				currentAudioItem3 = null;
			} else if (mNumExtraSounds == 0) {
				currentAudioItem2 = null;
				currentAudioItem3 = null;
			}
		}

		if (mNumExtraSounds == 0) {
			mSoundPoolPrepared = true;
		}

		FileInputStream playerInputStream = null;
		FileInputStream playerInputStream2 = null;
		FileInputStream playerInputStream3 = null;
		mSilenceFilePlaying = false;
		boolean dataLoaded = false;
		int dataLoadingErrorCount = 0;
		while (!dataLoaded && dataLoadingErrorCount <= 2) {
			try {
				mMediaPlayer.reset();
				mMediaPlayer2.reset();
				mMediaPlayer3.reset();

				if (currentAudioItem == null || (!(new File(currentAudioItem).exists()))) {
					mSilenceFilePlaying = true;
					if (mSilenceFileDescriptor == null) {
						mSilenceFileDescriptor = res.openRawResourceFd(R.raw.silence_100ms);
					}
					mMediaPlayer.setDataSource(mSilenceFileDescriptor.getFileDescriptor(),
							mSilenceFileDescriptor.getStartOffset(), mSilenceFileDescriptor.getDeclaredLength());
				} else {
					// can't play from data directory (they're private; permissions don't work), must use an input
					// stream - original was: mMediaPlayer.setDataSource(currentAudioItem);
					playerInputStream = new FileInputStream(new File(currentAudioItem));
					mMediaPlayer.setDataSource(playerInputStream.getFD());
					if (currentAudioItem2 != null) {
						playerInputStream2 = new FileInputStream(new File(currentAudioItem2));
						mMediaPlayer2.setDataSource(playerInputStream2.getFD());
					}
					if (currentAudioItem3 != null) {
						playerInputStream3 = new FileInputStream(new File(currentAudioItem3));
						mMediaPlayer3.setDataSource(playerInputStream3.getFD());
					}
				}
				dataLoaded = true;
			} catch (Throwable t) {
				// sometimes setDataSource fails for mysterious reasons - loop to open it, rather than failing
				dataLoaded = false;
				dataLoadingErrorCount += 1;
			} finally {
				IOUtilities.closeStream(playerInputStream);
				// IOUtilities.closeStream(playerInputStream2);
			}
		}

		try {
			if (dataLoaded) {
				mMediaPlayer.setLooping(false);
				mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
				mMediaPlayer2.setAudioStreamType(AudioManager.STREAM_MUSIC);
				mMediaPlayer3.setAudioStreamType(AudioManager.STREAM_MUSIC);
				mMediaPlayer.setOnPreparedListener(mMediaPlayerPreparedListener);
				// mMediaPlayer.setOnCompletionListener(mMediaPlayerCompletionListener);
				// done later - better pausing
				mMediaPlayer.setOnErrorListener(mMediaPlayerErrorListener);
				mMediaPlayer.prepareAsync();
				// @Haiyue
				// According to the number of recorded sound tracks, prepare corresponding media player
				switch (mNumExtraSounds) {
					case 1:
						mMediaPlayer2.prepareAsync();
						break;
					case 2:
						mMediaPlayer2.prepareAsync();
						mMediaPlayer3.prepareAsync();
						break;
				}
			} else {
				throw new IllegalStateException();
			}

		} catch (Throwable t) {
			UIUtilities.showToast(NarrativePlayerActivity.this, R.string.error_loading_narrative_player);
			onBackPressed();
			return;
		}

		// load the image
		if (container.mImagePath != null && new File(container.mImagePath).exists()) {
			Bitmap scaledBitmap = BitmapUtilities.loadAndCreateScaledBitmap(container.mImagePath,
					photoDisplay.getWidth(), photoDisplay.getHeight(), BitmapUtilities.ScalingLogic.FIT, true);
			mHasImage = true;
			photoDisplay.setImageBitmap(scaledBitmap);
			// @Haiyue
			// ScaleType has to be MATRIX, instead of CENTER_INSIDE
			// Corresponding changes need to be made in layout 'narrative_player.xml'
			photoDisplay.setScaleType(ScaleType.MATRIX);

		} else if (TextUtils.isEmpty(container.mTextContent)) { // no text and no image: audio icon
			if (mAudioPictureBitmap == null) {
				mAudioPictureBitmap = SVGParser.getSVGFromResource(res, R.raw.ic_audio_playback).getBitmap(
						photoDisplay.getWidth(), photoDisplay.getHeight());
			}
			photoDisplay.setImageBitmap(mAudioPictureBitmap);
			photoDisplay.setScaleType(ScaleType.FIT_CENTER);
			mHasImage = false;
		} else {
			photoDisplay.setImageDrawable(null);
			mHasImage = false;
		}

		// load the text
		AutoResizeTextView textView = (AutoResizeTextView) findViewById(R.id.text_playback);
		if (!TextUtils.isEmpty(container.mTextContent)) {
			textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, res.getDimensionPixelSize(R.dimen.playback_text));
			textView.setText(container.mTextContent);
			RelativeLayout.LayoutParams textLayout = new RelativeLayout.LayoutParams(
					RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
			textLayout.addRule(RelativeLayout.CENTER_HORIZONTAL);
			int textViewHeight = res.getDimensionPixelSize(R.dimen.media_controller_height);
			int textViewPadding = res.getDimensionPixelSize(R.dimen.playback_text_padding);
			if (container.mImagePath != null) {
				textView.setMaxHeight(res.getDimensionPixelSize(R.dimen.playback_maximum_text_height_with_image));
				textLayout.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
				textLayout.setMargins(0, 0, 0, (mMediaController.isShowing() ? textViewHeight : textViewPadding));
				textView.setBackgroundResource(R.drawable.rounded_playback_text);
				textView.setTextColor(res.getColor(R.color.export_text_with_image));
			} else {
				textView.setMaxHeight(photoDisplay.getHeight()); // no way to clear, so set to parent height
				textLayout.addRule(RelativeLayout.CENTER_VERTICAL);
				textLayout.setMargins(0, 0, 0, textViewPadding);
				textView.setBackgroundColor(res.getColor(android.R.color.transparent));
				textView.setTextColor(res.getColor(R.color.export_text_no_image));
			}
			textView.setLayoutParams(textLayout);
			textView.setVisibility(View.VISIBLE);
		} else {
			textView.setVisibility(View.GONE);
		}
	}

	// @Haiyue
	// Do the preparation for audio items and audio sync
	private void prepareAudioItems(String FrameId, Resources res) {

		// String currentAudioItem = null;
		// String currentAudioItem2 = null;
		// String currentAudioItem3 = null;

		SharedPreferences outAudioItem = getSharedPreferences(FrameId, 0);
		currentAudioItem = outAudioItem.getString("audioitem", null);
		currentAudioItem2 = outAudioItem.getString("audioitem1", null);
		currentAudioItem3 = outAudioItem.getString("audioitem2", null);

		FileInputStream playerInputStream = null;
		FileInputStream playerInputStream2 = null;
		FileInputStream playerInputStream3 = null;
		mSilenceFilePlaying = false;
		boolean dataLoaded = false;
		int dataLoadingErrorCount = 0;
		mSilencePlay = false;
		while (!dataLoaded && dataLoadingErrorCount <= 2) {
			try {
				mMediaPlayer.reset();
				mMediaPlayer2.reset();
				mMediaPlayer3.reset();

				if (currentAudioItem == null || (!(new File(currentAudioItem).exists()))) {
					Log.d("test", "no audio");
					mSilenceFilePlaying = true;
					if (mSilenceFileDescriptor == null) {
						mSilenceFileDescriptor = res.openRawResourceFd(R.raw.silence_100ms);
					}
					mMediaPlayer.setDataSource(mSilenceFileDescriptor.getFileDescriptor(),
							mSilenceFileDescriptor.getStartOffset(), mSilenceFileDescriptor.getDeclaredLength());
				} else {
					// can't play from data directory (they're private; permissions don't work), must use an input
					// stream - original was: mMediaPlayer.setDataSource(currentAudioItem);
					Log.d("test", "audio");
					playerInputStream = new FileInputStream(new File(currentAudioItem));
					mMediaPlayer.setDataSource(playerInputStream.getFD());
					if (currentAudioItem2 != null) {
						playerInputStream2 = new FileInputStream(new File(currentAudioItem2));
						mMediaPlayer2.setDataSource(playerInputStream2.getFD());
					}
					if (currentAudioItem3 != null) {
						playerInputStream3 = new FileInputStream(new File(currentAudioItem3));
						mMediaPlayer3.setDataSource(playerInputStream3.getFD());
					}
				}
				dataLoaded = true;
			} catch (Throwable t) {
				// sometimes setDataSource fails for mysterious reasons - loop to open it, rather than failing
				dataLoaded = false;
				dataLoadingErrorCount += 1;
			} finally {
				IOUtilities.closeStream(playerInputStream);
				// IOUtilities.closeStream(playerInputStream2);
			}
		}

		try {
			if (dataLoaded) {
				Log.d("start try", "audio");
				Log.d("number of sounds", String.valueOf(mNumExtraSounds));
				mMediaPlayer.setLooping(false);
				mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
				mMediaPlayer2.setAudioStreamType(AudioManager.STREAM_MUSIC);
				mMediaPlayer3.setAudioStreamType(AudioManager.STREAM_MUSIC);
				mMediaPlayer.setOnPreparedListener(mMediaPlayerPreparedListener);
				// mMediaPlayer.setOnCompletionListener(mMediaPlayerCompletionListener);
				// done later - better pausing
				mMediaPlayer.setOnErrorListener(mMediaPlayerErrorListener);
				mMediaPlayer.prepareAsync();
				switch (mNumExtraSounds) {
				// case 0:
				// mMediaPlayer.prepareAsync();
				// break;
					case 1:
						// mMediaPlayer.prepareAsync();
						mMediaPlayer2.prepareAsync();
						break;
					case 2:
						// mMediaPlayer.prepareAsync();
						mMediaPlayer2.prepareAsync();
						mMediaPlayer3.prepareAsync();
						break;
				}
			} else {
				throw new IllegalStateException();
			}

		} catch (Throwable t) {
			UIUtilities.showToast(NarrativePlayerActivity.this, R.string.error_loading_narrative_player);
			onBackPressed();
			return;
		}
	}

	private void unloadSoundPool() {
		for (Integer soundId : mFrameSounds) {
			mSoundPool.stop(soundId);
			mSoundPool.unload(soundId);
		}
		mFrameSounds.clear();
	}

	private void releasePlayer() {
		UIUtilities.releaseKeepScreenOn(getWindow());
		// release controller first, so we don't play to a null player
		if (mMediaController != null) {
			mMediaController.hide();
			((RelativeLayout) findViewById(R.id.narrative_playback_container)).removeView(mMediaController);
			mMediaController.setMediaPlayer(null);
			mMediaController = null;
		}
		if (mMediaPlayer != null) {
			try {
				mMediaPlayer.stop();
			} catch (IllegalStateException e) {
			}
			mMediaPlayer.release();
			mMediaPlayer = null;
		}
		if (mSoundPool != null) {
			unloadSoundPool();
			mSoundPool.release();
			mSoundPool = null;
		}
	}

	private CustomMediaController.MediaPlayerControl mMediaPlayerController = new CustomMediaController.MediaPlayerControl() {
		@Override
		public void start() {
			mPlaybackPauseTime = -1;
			if (mPlaybackPosition < 0) { // so we return to the start when playing from the end
				mPlaybackPosition = 0;
				mInitialPlaybackOffset = 0;
				mNonAudioOffset = 0;
				mCurrentFrameContainer = getMediaContainer(mPlaybackPosition, true);
				// @Haiyue
				// Make sure the image has correct original size each time to replay the narrative.
				resetZoom();

				prepareMediaItems(mCurrentFrameContainer);
			} else {
				if (mMediaPlayer != null // && mSoundPool != null
				) {
					mMediaPlayer.setOnCompletionListener(mMediaPlayerCompletionListener);
					mPlaybackStartTime = System.currentTimeMillis() - mMediaPlayer.getCurrentPosition();
					mMediaPlayer.start();
					mMediaPlayer2.start();
					mMediaPlayer3.start();
					// mSoundPool.autoResume(); // TODO: check this works
					showMediaController(CustomMediaController.DEFAULT_VISIBILITY_TIMEOUT);
				} else {
					UIUtilities.showToast(NarrativePlayerActivity.this, R.string.error_loading_narrative_player);
					onBackPressed();
					return;
				}
			}
			UIUtilities.acquireKeepScreenOn(getWindow());
		}

		@Override
		public void pause() {
			mIsLoading = false;
			if (mPlaybackPauseTime < 0) { // save the time paused, but don't overwrite if we call pause multiple times
				mPlaybackPauseTime = System.currentTimeMillis();
			}
			if (mMediaPlayer != null) {
				mMediaPlayer.setOnCompletionListener(null); // make sure we don't continue accidentally
				mMediaPlayer.pause();
			}
			if (mMediaPlayer2 != null) {
				mMediaPlayer2.pause();
			}
			if (mMediaPlayer3 != null) {
				mMediaPlayer3.pause();
			}

			showMediaController(-1); // to keep on showing until done here
			UIUtilities.releaseKeepScreenOn(getWindow());
		}

		@Override
		public int getDuration() {
			return mNarrativeDuration;
		}

		@Override
		public int getCurrentPosition() {
			if (mPlaybackPosition < 0) {
				return mNarrativeDuration;
			} else {
				int rootPlaybackPosition = mPlaybackPosition + mNonAudioOffset;
				if (mSilenceFilePlaying) {
					// must calculate the actual time at the point of pausing, rather than the current time
					if (mPlaybackPauseTime > 0) {
						rootPlaybackPosition += (int) (mPlaybackPauseTime - mPlaybackStartTime);
					} else {
						rootPlaybackPosition += (int) (System.currentTimeMillis() - mPlaybackStartTime);
					}
				} else {
					rootPlaybackPosition += (mMediaPlayer != null ? mMediaPlayer.getCurrentPosition() : 0);
				}
				return rootPlaybackPosition;
			}
		}

		@Override
		public void seekTo(int pos) {

			if (pos >= 0 && pos < mNarrativeDuration) {
				FrameMediaContainer newContainer = getMediaContainer(pos, true);
				if (newContainer != mCurrentFrameContainer) {
					mCurrentFrameContainer = newContainer;

					prepareMediaItems(mCurrentFrameContainer);
				}
			}
			// @Haiyue
			// Seek position for each recorded sound track
			int actualPos = pos - mPlaybackPosition;
			if (actualPos <= mMediaPlayer.getDuration()) {
				if (actualPos == mMediaPlayer.getDuration()) {
					mMediaPlayer.seekTo(actualPos);
					mMediaPlayer2.seekTo(actualPos);
					mMediaPlayer3.seekTo(actualPos);
					mMediaPlayer.start();
					mMediaPlayer2.start();
					mMediaPlayer3.start();
					mMediaController.setProgress();
				} else if (actualPos == 0) {
					mMediaPlayer.seekTo(actualPos);
					mMediaPlayer2.seekTo(actualPos);
					mMediaPlayer3.seekTo(actualPos);
					mMediaPlayer.start();
					mMediaPlayer2.start();
					mMediaPlayer3.start();
					mMediaController.setProgress();
				} else if (actualPos < mMediaPlayer.getDuration() && actualPos > mMediaPlayer2.getDuration()
						&& actualPos > mMediaPlayer3.getDuration()) {
					mMediaPlayer.seekTo(actualPos);
					mMediaPlayer.start();
					mMediaPlayer2.pause();
					mMediaPlayer3.pause();
					mMediaController.setProgress();
				} else if (actualPos < mMediaPlayer.getDuration() && actualPos > mMediaPlayer2.getDuration()
						&& actualPos < mMediaPlayer3.getDuration()) {
					mMediaPlayer.seekTo(actualPos);
					mMediaPlayer.start();
					mMediaPlayer2.pause();
					mMediaPlayer3.seekTo(actualPos);
					mMediaPlayer3.start();
					mMediaController.setProgress();
				} else if (actualPos < mMediaPlayer.getDuration() && actualPos > mMediaPlayer3.getDuration()
						&& actualPos < mMediaPlayer2.getDuration()) {
					mMediaPlayer.seekTo(actualPos);
					mMediaPlayer.start();
					mMediaPlayer3.pause();
					mMediaPlayer2.seekTo(actualPos);
					mMediaPlayer2.start();
					mMediaController.setProgress();
				} else if (actualPos < mMediaPlayer.getDuration() && actualPos < mMediaPlayer3.getDuration()
						&& actualPos < mMediaPlayer2.getDuration()) {
					mMediaPlayer.seekTo(actualPos);
					mMediaPlayer.start();
					mMediaPlayer2.seekTo(actualPos);
					mMediaPlayer2.start();
					mMediaPlayer3.seekTo(actualPos);
					mMediaPlayer3.start();
					mMediaController.setProgress();
				} else {
					// for image- or text-only frames
					mNonAudioOffset = actualPos;
					if (mMediaController.isDragging()) {
						mMediaPlayer.setOnCompletionListener(mMediaPlayerCompletionListener);
					}
					mPlaybackStartTime = System.currentTimeMillis();
					mMediaPlayer.seekTo(0); // TODO: seek others (is it even possible with soundpool?)
					mMediaPlayer.start();
					mMediaController.setProgress();
				}
			}

		}

		@Override
		public boolean isPlaying() {
			return mMediaPlayer == null ? false : mMediaPlayer.isPlaying();
		}

		@Override
		public boolean isLoading() {
			return mIsLoading;
		}

		@Override
		public int getBufferPercentage() {
			return 0;
		}

		@Override
		public boolean canPause() {
			return true;
		}

		@Override
		public boolean canSeekBackward() {
			return true;
		}

		@Override
		public boolean canSeekForward() {
			return true;
		}

		@Override
		public void onControllerVisibilityChange(boolean visible) {
			makeMediaItemsVisible(visible);
			if (visible) {
				UIUtilities.setNonFullScreen(getWindow());
			} else {
				UIUtilities.setFullScreen(getWindow());
			}
		}
	};

	private class TouchCallbackCustomMediaController extends CustomMediaController {

		private TouchCallbackCustomMediaController(Context context) {
			super(context);
		}

		@Override
		public boolean onInterceptTouchEvent(MotionEvent event) {
			switch (event.getAction()) {
				case MotionEvent.ACTION_DOWN:
					UIUtilities.setNonFullScreen(getWindow()); // so we don't have to wait for the playback bar to hide
																// before showing the notification bar
					break;
			}
			return false;
		}
	}

	private void startPlayers() {

		if (mIsLoading) {
			if (!mPlayFromFrameEditor) {
				if (mFrameSounds.size() == 0) {
					mediaVolume = fVolume1;
				}
				// @Haiyue
				// Transfer volume for media player
				if (mediaIndex == 0) {
					mediaVolume = cVolume1;

					volume1 = (float) (1 - (Math.log(15 - cVolume2) / Math.log(15)));
					volume2 = (float) (1 - (Math.log(15 - cVolume3) / Math.log(15)));
				} else if (mediaIndex == 1) {
					mediaVolume = cVolume2;
					volume1 = (float) (1 - (Math.log(15 - cVolume1) / Math.log(15)));
					volume2 = (float) (1 - (Math.log(15 - cVolume3) / Math.log(15)));
				} else if (mediaIndex == 2) {
					mediaVolume = cVolume3;
					volume1 = (float) (1 - (Math.log(15 - cVolume1) / Math.log(15)));

					volume2 = (float) (1 - (Math.log(15 - cVolume2) / Math.log(15)));

				}
			} else if (mPlayFromFrameEditor) {

				if (mFrameSounds.size() == 0) {
					mediaVolume = fVolume1;
				}
				if (mediaIndex == 0) {
					mediaVolume = fVolume1;
					volume1 = (float) (1 - (Math.log(15 - fVolume2) / Math.log(15)));
					volume2 = (float) (1 - (Math.log(15 - fVolume3) / Math.log(15)));
				} else if (mediaIndex == 1) {
					mediaVolume = fVolume2;

					volume1 = (float) (1 - (Math.log(15 - fVolume1) / Math.log(15)));

					volume2 = (float) (1 - (Math.log(15 - fVolume3) / Math.log(15)));

				} else if (mediaIndex == 2) {
					mediaVolume = fVolume3;

					volume1 = (float) (1 - (Math.log(15 - fVolume1) / Math.log(15)));

					volume2 = (float) (1 - (Math.log(15 - fVolume2) / Math.log(15)));

				}

			}

			mMediaPlayer.setOnCompletionListener(mMediaPlayerCompletionListener);
			mPlaybackStartTime = System.currentTimeMillis() - mInitialPlaybackOffset;
			mMediaPlayer.seekTo(mInitialPlaybackOffset);
			mMediaPlayer.start();
			mMediaPlayer2.start();
			mMediaPlayer3.start();

			// @Haiyue
			// set sound volume with longest duration for each narrative
			final float volume = (float) (1 - (Math.log(15 - mediaVolume) / Math.log(15)));
			mMediaPlayer.setVolume(volume, volume);
			mMediaPlayer2.setVolume(volume1, volume1);
			mMediaPlayer3.setVolume(volume2, volume2);
			mIsLoading = false;
			mPlayFromFrameEditor = false;
			mMediaController.setMediaPlayer(mMediaPlayerController);

			UIUtilities.acquireKeepScreenOn(getWindow());
		}
	}

	private SoundPool.OnLoadCompleteListener mSoundPoolLoadListener = new SoundPool.OnLoadCompleteListener() {
		@Override
		public void onLoadComplete(SoundPool soundPool, int sampleId, int status) {
			mFrameSounds.add(sampleId);
			if (mFrameSounds.size() >= mNumExtraSounds) {
				mSoundPoolPrepared = true;
			}
			if (mSoundPoolPrepared) {// && mMediaPlayerPrepared) {
				startPlayers();
			}
		}
	};

	private OnPreparedListener mMediaPlayerPreparedListener = new OnPreparedListener() {
		@Override
		public void onPrepared(MediaPlayer mp) {
			mMediaPlayerPrepared = true;
			if (mSoundPoolPrepared) {
				startPlayers();
			}
		}
	};

	private OnCompletionListener mMediaPlayerCompletionListener = new OnCompletionListener() {
		@Override
		public void onCompletion(MediaPlayer mp) {
			if (mMediaPlayerError) {
				// releasePlayer(); // don't do this, as it means the player will be null; instead we resume from errors
				mCurrentFrameContainer = getMediaContainer(mPlaybackPosition, false);

				prepareMediaItems(mCurrentFrameContainer);
				mMediaPlayerError = false;
				return;
			}
			mInitialPlaybackOffset = 0;
			int currentPosition = mMediaPlayerController.getCurrentPosition()
					+ (mMediaPlayer.getDuration() - mMediaPlayer.getCurrentPosition()) + 1;
			if (currentPosition < mNarrativeDuration) {
				mMediaPlayerController.seekTo(currentPosition);
			} else if (!mMediaController.isDragging()) {
				// move to just before the end (accounting for mNarrativeDuration errors)
				mMediaPlayerController.seekTo(currentPosition - 2);
				pauseMediaController(); // will also show the controller if applicable
				mPlaybackPosition = -1; // so we start from the beginning
			}
		}
	};

	private OnErrorListener mMediaPlayerErrorListener = new OnErrorListener() {
		@Override
		public boolean onError(MediaPlayer mp, int what, int extra) {
			mMediaPlayerError = true;
			// UIUtilities.showToast(NarrativePlayerActivity.this, R.string.error_loading_narrative_player);
			if (MediaPhone.DEBUG)
				Log.d(DebugUtilities.getLogTag(this), "Playback error - what: " + what + ", extra: " + extra);
			return false; // not handled -> onCompletionListener will be called
		}
	};

	// @Haiyue
	// onTouch events for zoom in/out the image
	public boolean onTouch(View v, MotionEvent event) {

		ImageView view = (ImageView) v;

		switch (event.getAction() & MotionEvent.ACTION_MASK) {
			case MotionEvent.ACTION_DOWN:
				showMediaController(CustomMediaController.DEFAULT_VISIBILITY_TIMEOUT);
				savedMatrix.set(matrix);
				start.set(event.getX(), event.getY());
				mode = DRAG;
				break;

			case MotionEvent.ACTION_POINTER_DOWN:
				oldDist = spacing(event);
				if (oldDist > 10f) {
					savedMatrix.set(matrix);
					midPoint(mid, event);
					mode = ZOOM;
				}
				break;

			case MotionEvent.ACTION_UP:

			case MotionEvent.ACTION_POINTER_UP:
				mode = NONE;
				break;

			case MotionEvent.ACTION_MOVE:
				if (mode == DRAG) {
					matrix.set(savedMatrix);
					matrix.postTranslate(event.getX() - start.x, event.getY() - start.y);
				} else if (mode == ZOOM) {
					float newDist = spacing(event);
					if (newDist > 10f) {
						matrix.set(savedMatrix);
						float scale = newDist / oldDist;
						matrix.postScale(scale, scale, mid.x, mid.y);
					}
				}
				break;
		}
		view.setImageMatrix(matrix);
		return true; // indicate event was handled
	}

	// @Haiyue
	// Determine the space between the first two fingers
	private float spacing(MotionEvent event) {
		float x = event.getX(0) - event.getX(1);
		float y = event.getY(0) - event.getY(1);
		return FloatMath.sqrt(x * x + y * y);
	}

	// @Haiyue
	// Calculate the mid point of the first two fingers
	private void midPoint(PointF point, MotionEvent event) {
		float x = event.getX(0) + event.getX(1);
		float y = event.getY(0) + event.getY(1);
		point.set(x / 2, y / 2);
	}

	// @Haiyue
	// reset image size, position
	private void resetZoom() {
		matrix.reset();
		savedMatrix.reset();
		start.set(0.0f, 0.0f);
		mid.set(0.0f, 0.0f);
		photoDisplay.setScaleType(ScaleType.CENTER_INSIDE);
		// photoDisplay.setScaleType(ScaleType.FIT_CENTER);
	}
}
