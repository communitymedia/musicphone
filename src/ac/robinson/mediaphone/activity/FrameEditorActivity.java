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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map.Entry;

import ac.robinson.mediaphone.MediaPhone;
import ac.robinson.mediaphone.MediaPhoneActivity;
import ac.robinson.musicphone.R;
import ac.robinson.mediaphone.provider.FrameItem;
import ac.robinson.mediaphone.provider.FramesManager;
import ac.robinson.mediaphone.provider.MediaItem;
import ac.robinson.mediaphone.provider.MediaManager;
import ac.robinson.mediaphone.provider.MediaPhoneProvider;
import ac.robinson.mediaphone.provider.NarrativeItem;
import ac.robinson.mediaphone.provider.NarrativesManager;
import ac.robinson.util.BitmapUtilities;
import ac.robinson.util.IOUtilities;
import ac.robinson.util.StringUtilities;
import ac.robinson.util.UIUtilities;
import ac.robinson.view.CenteredImageTextButton;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.drawable.BitmapDrawable;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;

public class FrameEditorActivity extends MediaPhoneActivity implements OnSeekBarChangeListener {

	// not in MediaPhone.java because it needs more than just this to add more audio items (layouts need updating too)
	private final int MAX_AUDIO_ITEMS = 3;

	private String mFrameInternalId;
	private boolean mHasEditedMedia = false;
	private boolean mShowOptionsMenu = false;
	private boolean mAddNewFrame = false;
	private String mReloadImagePath = null;
	private boolean mDeleteFrameOnExit = false;
	private LinkedHashMap<String, Integer> mFrameAudioItems = new LinkedHashMap<String, Integer>();

	// private SeekBar[] sliders;
	private SeekBar audio1Progress, audio2Progress, audio3Progress;
	private SeekBar[] audioProgress;
	private AudioManager mAudioMan1;
	private AudioManager mAudioMan2;
	private AudioManager mAudioMan3;
	private int maxVol1, curVol1, maxVol2, curVol2, maxVol3, curVol3;
	private int volume1, volume2, volume3;
	private boolean audio1Pressed = false, audio2Pressed = false, audio3Pressed = false;
	private boolean audio1ButtonVisibility = false, audio2ButtonVisibility = false, audio3ButtonVisibility = false;
	public static final String VOLUME_SAVE = "volume_save";
	private boolean mPlayFromFrameEditor = false;
	private String RECORD_AUDIO1 = "recorded audio 1";
	private String RECORD_AUDIO2 = "recorded audio 2";
	private String RECORD_AUDIO3 = "recorded audio 3";
	private String audio1id, audio2id, audio3id, audio1id_up, audio2id_up, audio3id_up;
	private boolean audioDeletedneedUpdated = false;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		// @Haiyue
		// frame editor view has been changed, check the xml layout file
		super.onCreate(savedInstanceState);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			setTheme(R.style.default_light_theme); // light looks *much* better beyond honeycomb
		}
		UIUtilities.configureActionBar(this, true, true, R.string.title_frame_editor, 0);
		setContentView(R.layout.frame_editor);

		// load previous id on screen rotation
		mFrameInternalId = null;
		if (savedInstanceState != null) {
			mFrameInternalId = savedInstanceState.getString(getString(R.string.extra_internal_id));
			mHasEditedMedia = savedInstanceState.getBoolean(getString(R.string.extra_media_edited));
			if (mHasEditedMedia) {
				setBackButtonIcons(FrameEditorActivity.this, R.id.button_finished_editing, 0, true);
			}

		}
		// @Haiyue
		// add seek bar
		audio1Progress = (SeekBar) findViewById(R.id.seekBar1);
		audio2Progress = (SeekBar) findViewById(R.id.seekBar2);
		audio3Progress = (SeekBar) findViewById(R.id.seekBar3);

		mAudioMan1 = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
		maxVol1 = mAudioMan1.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
		curVol1 = mAudioMan1.getStreamVolume(AudioManager.STREAM_MUSIC);

		mAudioMan2 = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
		maxVol2 = mAudioMan2.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
		curVol2 = mAudioMan2.getStreamVolume(AudioManager.STREAM_MUSIC);

		mAudioMan3 = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
		maxVol3 = mAudioMan3.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
		curVol3 = mAudioMan3.getStreamVolume(AudioManager.STREAM_MUSIC);

		audio1Progress.setMax(maxVol1);
		audio2Progress.setMax(maxVol2);
		audio3Progress.setMax(maxVol3);

		audio1Progress.setOnSeekBarChangeListener(this);
		audio2Progress.setOnSeekBarChangeListener(this);
		audio3Progress.setOnSeekBarChangeListener(this);

		audio1Pressed = false;
		audio2Pressed = false;
		audio3Pressed = false;
		// load the frame elements themselves
		loadFrameElements();

	}

	@Override
	public void onSaveInstanceState(Bundle savedInstanceState) {
		savedInstanceState.putString(getString(R.string.extra_internal_id), mFrameInternalId);
		savedInstanceState.putBoolean(getString(R.string.extra_media_edited), mHasEditedMedia);
		super.onSaveInstanceState(savedInstanceState);
	}

	@Override
	public void onWindowFocusChanged(boolean hasFocus) {
		super.onWindowFocusChanged(hasFocus);
		if (hasFocus) {
			if (mAddNewFrame) {
				mAddNewFrame = false;
				addNewFrame();
				saveLastEditedFrame(mFrameInternalId); // this is now the last edited frame
			} else {
				// change the frame that is displayed, if applicable
				changeFrames(loadLastEditedFrame());

				// do image loading here so that we know the layout's size for sizing the image
				if (mReloadImagePath != null) {
					reloadFrameImage(mReloadImagePath);
					mReloadImagePath = null;
				}

				if (mShowOptionsMenu) {
					mShowOptionsMenu = false;
					openOptionsMenu();
				}
			}
			registerForSwipeEvents(); // here to avoid crashing due to double-swiping
		}
	}

	@Override
	public void onBackPressed() {

		audio1Pressed = false;
		audio2Pressed = false;
		audio3Pressed = false;
		// @Haiyue
		// save volume information after pressing back button
		SharedPreferences inputPrefs = getSharedPreferences(mFrameInternalId, 0);
		Editor editor = inputPrefs.edit();
		editor.putInt("volume1", volume1);
		editor.putInt("volume2", volume2);
		editor.putInt("volume3", volume3);
		editor.commit();

		// managed to press back before loading the frame - wait
		if (mFrameInternalId == null) {
			return;
		}

		// delete frame/narrative if required
		Resources resources = getResources();
		ContentResolver contentResolver = getContentResolver();
		final FrameItem editedFrame = FramesManager.findFrameByInternalId(contentResolver, mFrameInternalId);
		if (MediaManager.countMediaByParentId(contentResolver, mFrameInternalId) <= 0 || mDeleteFrameOnExit) {
			// need the next frame id for scrolling (but before we update it to be deleted)
			ArrayList<String> frameIds = FramesManager.findFrameIdsByParentId(contentResolver,
					editedFrame.getParentId());

			int numFrames = frameIds.size();
			if (numFrames > 1) { // don't save if we're the last frame
				int i = 0;
				int foundId = -1;
				for (String id : frameIds) {
					if (mFrameInternalId.equals(id)) {
						foundId = i;
						break;
					}
					i += 1;
				}
				if (foundId >= 0) {
					int idCount = numFrames - 2; // so we scroll to the last frame after this is deleted
					foundId = foundId > idCount ? idCount : foundId;
					saveLastEditedFrame(frameIds.get(foundId)); // scroll to this frame after exiting
				}
			}

			editedFrame.setDeleted(true);
			FramesManager.updateFrame(contentResolver, editedFrame);
		} else {
			saveLastEditedFrame(mFrameInternalId); // so we always get the id even if we've done next/prev
		}

		// delete, or added no frame content
		if (editedFrame.getDeleted()) {
			// no narrative content - delete the narrative first for a better interface experience
			// (don't have to wait for the frame to disappear)
			int numFrames = FramesManager.countFramesByParentId(contentResolver, editedFrame.getParentId());
			if (numFrames == 0) {
				NarrativeItem narrativeToDelete = NarrativesManager.findNarrativeByInternalId(contentResolver,
						editedFrame.getParentId());
				narrativeToDelete.setDeleted(true);
				NarrativesManager.updateNarrative(contentResolver, narrativeToDelete);

			} else if (numFrames > 0) {
				// if we're the first frame, update the second frame's icon to be the main icon (i.e. with overlay)
				FrameItem nextFrame = FramesManager
						.findFirstFrameByParentId(contentResolver, editedFrame.getParentId());
				if (editedFrame.getNarrativeSequenceId() < nextFrame.getNarrativeSequenceId()) {
					FramesManager.reloadFrameIcon(resources, contentResolver, nextFrame, true);
				}
			}
		}

		setResult(Activity.RESULT_OK);
		mPlayFromFrameEditor = false;
		super.onBackPressed();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// TODO: if we couldn't open a temporary directory then exporting won't work
		MenuInflater inflater = getMenuInflater();
		setupMenuNavigationButtons(inflater, menu, mFrameInternalId, mHasEditedMedia);
		inflater.inflate(R.menu.play_narrative, menu);
		inflater.inflate(R.menu.make_template, menu);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			inflater.inflate(R.menu.delete_narrative, menu); // no space pre action bar
		}
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		final int itemId = item.getItemId();
		switch (itemId) {
			case R.id.menu_previous_frame:
			case R.id.menu_next_frame:
				switchFrames(mFrameInternalId, itemId, R.string.extra_internal_id, true, FrameEditorActivity.class);
				return true;

			case R.id.menu_export_narrative:
				// note: not currently possible (menu item removed for consistency), but left for possible future use
				FrameItem exportFrame = FramesManager.findFrameByInternalId(getContentResolver(), mFrameInternalId);
				if (exportFrame != null) {
					exportContent(exportFrame.getParentId(), false);
				}
				return true;

			case R.id.menu_play_narrative:
				if (MediaManager.countMediaByParentId(getContentResolver(), mFrameInternalId) > 0) {
					final Intent framePlayerIntent = new Intent(FrameEditorActivity.this, NarrativePlayerActivity.class);
					framePlayerIntent.putExtra(getString(R.string.extra_internal_id), mFrameInternalId);

					// @Haiyue
					// load volume
					SharedPreferences outputPrefs = getSharedPreferences(mFrameInternalId, 0);
					volume1 = outputPrefs.getInt("volume1", -1);
					volume2 = outputPrefs.getInt("volume2", -1);
					volume3 = outputPrefs.getInt("volume3", -1);

					framePlayerIntent.putExtra("Current Volume1 From FrameEditor", volume1);
					framePlayerIntent.putExtra("Current Volume2 From FrameEditor", volume2);
					framePlayerIntent.putExtra("Current Volume3 From FrameEditor", volume3);
					mPlayFromFrameEditor = true;
					framePlayerIntent.putExtra("Play from editor", mPlayFromFrameEditor);
					startActivityForResult(framePlayerIntent, MediaPhone.R_id_intent_narrative_player);
				} else {
					UIUtilities.showToast(FrameEditorActivity.this, R.string.play_narrative_add_content);
				}
				return true;

			case R.id.menu_add_frame:
				ContentResolver contentResolver = getContentResolver();
				if (MediaManager.countMediaByParentId(contentResolver, mFrameInternalId) > 0) {
					FrameItem currentFrame = FramesManager.findFrameByInternalId(contentResolver, mFrameInternalId);
					if (currentFrame != null) {
						final Intent frameEditorIntent = new Intent(FrameEditorActivity.this, FrameEditorActivity.class);
						frameEditorIntent.putExtra(getString(R.string.extra_parent_id), currentFrame.getParentId());
						frameEditorIntent.putExtra(getString(R.string.extra_insert_after_id), mFrameInternalId);
						startActivity(frameEditorIntent); // no result so that the original exits TODO: does it?
						onBackPressed();
					}
				} else {
					UIUtilities.showToast(FrameEditorActivity.this, R.string.split_frame_add_content);
				}
				return true;

			case R.id.menu_make_template:
				ContentResolver resolver = getContentResolver();
				if (MediaManager.countMediaByParentId(resolver, mFrameInternalId) > 0) {
					FrameItem currentFrame = FramesManager.findFrameByInternalId(resolver, mFrameInternalId);
					runQueuedBackgroundTask(getNarrativeTemplateRunnable(currentFrame.getParentId(),
							MediaPhoneProvider.getNewInternalId(), true)); // don't need the id
				} else {
					UIUtilities.showToast(FrameEditorActivity.this, R.string.make_template_add_content);
				}
				return true;

			case R.id.menu_delete_narrative:
				deleteNarrativeDialog(mFrameInternalId);
				return true;

			case R.id.menu_back_without_editing:
			case R.id.menu_finished_editing:
				onBackPressed();
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
		// the soft done/back button
		int newVisibility = View.VISIBLE;
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB
				|| !mediaPhoneSettings.getBoolean(getString(R.string.key_show_back_button),
						getResources().getBoolean(R.bool.default_show_back_button))) {
			newVisibility = View.GONE;
		}
		findViewById(R.id.button_finished_editing).setVisibility(newVisibility);
	}

	private void loadFrameElements() {

		mAddNewFrame = false;
		if (mFrameInternalId == null) {
			// editing an existing frame
			final Intent intent = getIntent();
			if (intent != null) {
				mFrameInternalId = intent.getStringExtra(getString(R.string.extra_internal_id));
				mShowOptionsMenu = intent.getBooleanExtra(getString(R.string.extra_show_options_menu), false);
			}

			// adding a new frame
			if (mFrameInternalId == null) {
				mAddNewFrame = true;
			}
		}

		// reset interface
		mReloadImagePath = null;
		mFrameAudioItems.clear();

		((CenteredImageTextButton) findViewById(R.id.button_take_picture_video))
				.setCompoundDrawablesWithIntrinsicBounds(0, android.R.drawable.ic_menu_camera, 0, 0);
		// (audio buttons are loaded/reset after audio files are loaded)
		((CenteredImageTextButton) findViewById(R.id.button_add_text)).setText("");

		// load existing content into buttons
		if (!mAddNewFrame) {
			ArrayList<MediaItem> frameComponents = MediaManager.findMediaByParentId(getContentResolver(),
					mFrameInternalId);
			boolean imageLoaded = false;
			boolean audioLoaded = false;
			boolean textLoaded = false;
			for (MediaItem currentItem : frameComponents) {
				final int currentType = currentItem.getType();
				if (!imageLoaded
						&& (currentType == MediaPhoneProvider.TYPE_IMAGE_BACK
								|| currentType == MediaPhoneProvider.TYPE_IMAGE_FRONT || currentType == MediaPhoneProvider.TYPE_VIDEO)) {
					mReloadImagePath = currentItem.getFile().getAbsolutePath();
					imageLoaded = true;

				} else if (!audioLoaded && currentType == MediaPhoneProvider.TYPE_AUDIO) {
					mFrameAudioItems.put(currentItem.getInternalId(), currentItem.getDurationMilliseconds());
					if (mFrameAudioItems.size() >= MAX_AUDIO_ITEMS) {
						audioLoaded = true;
					}

				} else if (!textLoaded && currentType == MediaPhoneProvider.TYPE_TEXT) {
					String textSnippet = IOUtilities.getFileContentSnippet(currentItem.getFile().getAbsolutePath(),
							getResources().getInteger(R.integer.text_snippet_length));
					((CenteredImageTextButton) findViewById(R.id.button_add_text)).setText(textSnippet);
					textLoaded = true;
				}
			}

			saveLastEditedFrame(mFrameInternalId); // this is now the last edited frame
		}

		// update the interface (image is loaded in onWindowFocusChanged so we know the button's size)
		reloadAudioButtons();

	}

	private void addNewFrame() {
		final Intent intent = getIntent();
		if (intent == null) {
			UIUtilities.showToast(FrameEditorActivity.this, R.string.error_loading_frame_editor);
			onBackPressed();
			return;
		}

		String intentNarrativeId = intent.getStringExtra(getString(R.string.extra_parent_id));
		final boolean insertNewNarrative = intentNarrativeId == null;
		final String narrativeId = insertNewNarrative ? MediaPhoneProvider.getNewInternalId() : intentNarrativeId;
		final String insertBeforeId = intent.getStringExtra(getString(R.string.extra_insert_before_id));
		final String insertAfterId = intent.getStringExtra(getString(R.string.extra_insert_after_id));

		// don't load the frame's icon yet - it will be loaded (or deleted) when we return
		Resources res = getResources();
		ContentResolver contentResolver = getContentResolver();
		FrameItem newFrame = new FrameItem(narrativeId, -1);
		FramesManager.addFrame(res, contentResolver, newFrame, false);
		mFrameInternalId = newFrame.getInternalId();

		// note: not a background task any more, because it causes concurrency problems with deleting after back press
		int narrativeSequenceIdIncrement = res.getInteger(R.integer.frame_narrative_sequence_increment);
		int narrativeSequenceId = 0;

		if (insertNewNarrative) {
			// new narrative required
			NarrativeItem newNarrative = new NarrativeItem(narrativeId,
					NarrativesManager.getNextNarrativeExternalId(contentResolver));
			NarrativesManager.addNarrative(contentResolver, newNarrative);

		} else {
			// default to inserting at the end if no before/after id is given
			if (insertBeforeId == null && insertAfterId == null) {
				narrativeSequenceId = FramesManager.findLastFrameNarrativeSequenceId(contentResolver, narrativeId)
						+ narrativeSequenceIdIncrement;

			} else {
				// insert new frame - increment necessary frames after the new frame's position
				boolean insertAtStart = FrameItem.KEY_FRAME_ID_START.equals(insertBeforeId);
				ArrayList<FrameItem> narrativeFrames = FramesManager.findFramesByParentId(contentResolver, narrativeId);
				narrativeFrames.remove(0); // don't edit the newly inserted frame yet

				int previousNarrativeSequenceId = -1;
				boolean frameFound = false;
				for (FrameItem frame : narrativeFrames) {
					if (!frameFound && (insertAtStart || frame.getInternalId().equals(insertBeforeId))) {
						frameFound = true;
						narrativeSequenceId = frame.getNarrativeSequenceId();
					}
					if (frameFound) {
						int currentNarrativeSequenceId = frame.getNarrativeSequenceId();
						if (currentNarrativeSequenceId <= narrativeSequenceId
								|| currentNarrativeSequenceId <= previousNarrativeSequenceId) {

							frame.setNarrativeSequenceId(currentNarrativeSequenceId
									+ Math.max(narrativeSequenceId - currentNarrativeSequenceId,
											previousNarrativeSequenceId - currentNarrativeSequenceId) + 1);
							if (insertAtStart) {
								FramesManager.updateFrame(res, contentResolver, frame, true);
								insertAtStart = false;
							} else {
								FramesManager.updateFrame(contentResolver, frame);
							}
							previousNarrativeSequenceId = frame.getNarrativeSequenceId();
						} else {
							break;
						}
					}
					if (!frameFound && frame.getInternalId().equals(insertAfterId)) {
						frameFound = true;
						narrativeSequenceId = frame.getNarrativeSequenceId() + narrativeSequenceIdIncrement;
					}
				}
			}
		}

		FrameItem thisFrame = FramesManager.findFrameByInternalId(contentResolver, mFrameInternalId);
		thisFrame.setNarrativeSequenceId(narrativeSequenceId);
		FramesManager.updateFrame(contentResolver, thisFrame);

	}

	private void reloadAudioButtons() {
		CenteredImageTextButton[] audioButtons = { (CenteredImageTextButton) findViewById(R.id.button_record_audio_1),
				(CenteredImageTextButton) findViewById(R.id.button_record_audio_2),
				(CenteredImageTextButton) findViewById(R.id.button_record_audio_3) };
		audioButtons[2].setText("");

		// load the audio content
		int audioIndex = 0;
		for (Entry<String, Integer> audioMedia : mFrameAudioItems.entrySet()) {
			audioButtons[audioIndex].setText(StringUtilities.millisecondsToTimeString(audioMedia.getValue(), false));
			audioIndex += 1;
			// progressIndex += 1;
		}

		// @Haiyue
		// hide unnecessary buttons
		if (audioIndex == 1) {
			audioButtons[2].setVisibility(View.GONE);
			audioButtons[1].setVisibility(View.VISIBLE);
			audioButtons[0].setVisibility(View.VISIBLE);
			audio3Progress.setVisibility(View.GONE);
			audio2Progress.setVisibility(View.GONE);
			audio1Progress.setVisibility(View.VISIBLE);
			audio1ButtonVisibility = true;
			audio2ButtonVisibility = false;
			audio3ButtonVisibility = false;
			audioButtons[1].setText("");
		} else if (audioIndex == 2) {
			audioButtons[2].setVisibility(View.VISIBLE);
			audioButtons[1].setVisibility(View.VISIBLE);
			audioButtons[0].setVisibility(View.VISIBLE);
			audioButtons[2].setText("");
			audio3Progress.setVisibility(View.GONE);
			audio2Progress.setVisibility(View.VISIBLE);
			audio1Progress.setVisibility(View.VISIBLE);
			audio1ButtonVisibility = true;
			audio2ButtonVisibility = true;
			audio3ButtonVisibility = false;
		} else if (audioIndex == 3) {
			audio3Progress.setVisibility(View.VISIBLE);
			audio2Progress.setVisibility(View.VISIBLE);
			audio1Progress.setVisibility(View.VISIBLE);
			audioButtons[2].setVisibility(View.VISIBLE);
			audioButtons[1].setVisibility(View.VISIBLE);
			audioButtons[0].setVisibility(View.VISIBLE);
			audio1ButtonVisibility = true;
			audio2ButtonVisibility = true;
			audio3ButtonVisibility = true;
		} else if (audioIndex == 0) {
			audio3Progress.setVisibility(View.GONE);
			audio2Progress.setVisibility(View.GONE);
			audio1Progress.setVisibility(View.GONE);
			audioButtons[2].setVisibility(View.GONE);
			audioButtons[1].setVisibility(View.GONE);
			audioButtons[0].setVisibility(View.VISIBLE);
			audioButtons[0].setText("");
			audio1ButtonVisibility = false;
			audio2ButtonVisibility = false;
			audio3ButtonVisibility = false;
		}

		// @Haiyue
		// load volume
		SharedPreferences outputPrefs = getSharedPreferences(mFrameInternalId, 0);
		if (mFrameInternalId != null)
			Log.d("input frame id ", mFrameInternalId);
		volume1 = outputPrefs.getInt("volume1", 0);
		volume2 = outputPrefs.getInt("volume2", 0);
		volume3 = outputPrefs.getInt("volume3", 0);
		audio1id_up = outputPrefs.getString("upaudio1", null);
		audio2id_up = outputPrefs.getString("upaudio2", null);
		audio3id_up = outputPrefs.getString("upaudio3", null);
		audioDeletedneedUpdated = outputPrefs.getBoolean("audioDeleted", false);
		Log.d("string delete", String.valueOf(audioDeletedneedUpdated));
		if (audioDeletedneedUpdated) {
			SharedPreferences inputPrefs = getSharedPreferences(mFrameInternalId, 0);
			Editor editor = inputPrefs.edit();
			editor.putString("curaudio1", audio1id_up);
			editor.putString("curaudio2", audio2id_up);
			editor.putString("curaudio3", audio3id_up);
			editor.commit();
		}
		audioDeletedneedUpdated = false;

		// @Haiyue
		// set volume bar progress using the loaded volume information
		audio1Progress.setProgress(volume1);
		audio2Progress.setProgress(volume2);
		audio3Progress.setProgress(volume3);
	}

	private void reloadFrameImage(String imagePath) {
		CenteredImageTextButton cameraButton = (CenteredImageTextButton) findViewById(R.id.button_take_picture_video);
		Resources resources = getResources();
		TypedValue resourceValue = new TypedValue();
		resources.getValue(R.attr.image_button_fill_percentage, resourceValue, true);
		int pictureSize = (int) ((resources.getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE ? cameraButton
				.getWidth() : cameraButton.getHeight()) * resourceValue.getFloat());
		BitmapDrawable cachedIcon = new BitmapDrawable(resources, BitmapUtilities.loadAndCreateScaledBitmap(imagePath,
				pictureSize, pictureSize, BitmapUtilities.ScalingLogic.CROP, true));
		cameraButton.setCompoundDrawablesWithIntrinsicBounds(null, cachedIcon, null, null);
	}

	private void changeFrames(String newFrameId) {
		if (newFrameId != null && !newFrameId.equals(mFrameInternalId)) {
			mFrameInternalId = newFrameId;
			String extraKey = getString(R.string.extra_internal_id);
			final Intent launchingIntent = getIntent();
			if (launchingIntent != null) {
				launchingIntent.removeExtra(extraKey);
				launchingIntent.putExtra(extraKey, mFrameInternalId);
				setIntent(launchingIntent);
			}

			// assume they've edited (maybe not, but we can't get the result, so don't know) - refreshes action bar too
			mHasEditedMedia = true;
			setBackButtonIcons(FrameEditorActivity.this, R.id.button_finished_editing, 0, true);

			// load the new frame elements
			loadFrameElements();
		}
	}

	@Override
	protected boolean swipeNext() {
		return switchFrames(mFrameInternalId, R.id.menu_next_frame, R.string.extra_internal_id, false,
				FrameEditorActivity.class);
	}

	@Override
	protected boolean swipePrevious() {
		return switchFrames(mFrameInternalId, R.id.menu_previous_frame, R.string.extra_internal_id, false,
				FrameEditorActivity.class);
	}

	private int getAudioIndex(int buttonId) {
		switch (buttonId) {
			case R.id.button_record_audio_1:
				return 0;
			case R.id.button_record_audio_2:
				return 1;
			case R.id.button_record_audio_3:
				return 2;
		}
		return -1;
	}

	public void handleButtonClicks(View currentButton) {
		audio1Pressed = false;
		audio2Pressed = false;
		audio3Pressed = false;

		if (!verifyButtonClick(currentButton)) {
			return;
		}

		final int buttonId = currentButton.getId();
		switch (buttonId) {
			case R.id.button_finished_editing:
				onBackPressed();
				break;

			case R.id.button_take_picture_video:
				final Intent takePictureIntent = new Intent(FrameEditorActivity.this, CameraActivity.class);
				takePictureIntent.putExtra(getString(R.string.extra_parent_id), mFrameInternalId);
				startActivityForResult(takePictureIntent, MediaPhone.R_id_intent_picture_editor);
				break;

			// @Haiyue
			// press allocated button to record
			case R.id.button_record_audio_1:
			case R.id.button_record_audio_2:
			case R.id.button_record_audio_3:
				SharedPreferences sh = getSharedPreferences(mFrameInternalId, 0);
				final Intent recordAudioIntent = new Intent(FrameEditorActivity.this, AudioActivity.class);
				recordAudioIntent.putExtra(getString(R.string.extra_parent_id), mFrameInternalId);

				int selectedAudioIndex = getAudioIndex(buttonId);
				int currentIndex = 0;
				for (String audioMediaId : mFrameAudioItems.keySet()) {

					if (audio1ButtonVisibility == true && audio2ButtonVisibility == false
							&& audio3ButtonVisibility == false)
						audio1id = audioMediaId;

					if (audio1ButtonVisibility == true && audio2ButtonVisibility == true
							&& audio3ButtonVisibility == false) {
						audio1id = sh.getString("curaudio1", null);
						audio2id = audioMediaId;
					}
					if (audio1ButtonVisibility == true && audio2ButtonVisibility == true
							&& audio3ButtonVisibility == true) {
						audio1id = sh.getString("curaudio1", null);
						audio2id = sh.getString("curaudio2", null);
						audio3id = audioMediaId;
					}
					if (currentIndex == selectedAudioIndex) {
						recordAudioIntent.putExtra(getString(R.string.extra_internal_id), audioMediaId);
						// @Haiyue
						// set volume for preview playback
						if (selectedAudioIndex == 0) {
							mAudioMan1.setStreamVolume(AudioManager.STREAM_MUSIC, volume1, 0);
							audio1Pressed = true;
							audio1id = audioMediaId;

						}
						if (selectedAudioIndex == 1) {
							mAudioMan2.setStreamVolume(AudioManager.STREAM_MUSIC, volume2, 0);
							audio2Pressed = true;
							audio2id = audioMediaId;
						}
						if (selectedAudioIndex == 2) {
							mAudioMan3.setStreamVolume(AudioManager.STREAM_MUSIC, volume3, 0);
							audio3Pressed = true;
							audio3id = audioMediaId;
						}
						break;
					}
					currentIndex += 1;
				}

				recordAudioIntent.putExtra("volume1", volume1);
				recordAudioIntent.putExtra("volume2", volume2);
				recordAudioIntent.putExtra("volume3", volume3);

				recordAudioIntent.putExtra("pressAudio1", audio1Pressed);
				recordAudioIntent.putExtra("pressAudio2", audio2Pressed);
				recordAudioIntent.putExtra("pressAudio3", audio3Pressed);

				recordAudioIntent.putExtra("buttonAudio1", audio1ButtonVisibility);
				recordAudioIntent.putExtra("buttonAudio2", audio2ButtonVisibility);
				recordAudioIntent.putExtra("buttonAudio3", audio3ButtonVisibility);

				recordAudioIntent.putExtra("audio1mediaid", audio1id);
				recordAudioIntent.putExtra("audio2mediaid", audio2id);
				recordAudioIntent.putExtra("audio3mediaid", audio3id);

				recordAudioIntent.putExtra("frameID", mFrameInternalId);
				startActivityForResult(recordAudioIntent, MediaPhone.R_id_intent_audio_editor);
				SharedPreferences inputPrefs = getSharedPreferences(mFrameInternalId, 0);
				Editor editor = inputPrefs.edit();
				editor.putString("curaudio1", audio1id);
				editor.putString("curaudio2", audio2id);
				editor.putString("curaudio3", audio3id);
				editor.commit();
				break;

			case R.id.button_add_text:
				final Intent addTextIntent = new Intent(FrameEditorActivity.this, TextActivity.class);
				addTextIntent.putExtra(getString(R.string.extra_parent_id), mFrameInternalId);
				startActivityForResult(addTextIntent, MediaPhone.R_id_intent_text_editor);
				break;

			case R.id.button_delete_frame:
				AlertDialog.Builder builder = new AlertDialog.Builder(FrameEditorActivity.this);
				builder.setTitle(R.string.delete_frame_confirmation);
				builder.setMessage(R.string.delete_frame_hint);
				builder.setIcon(android.R.drawable.ic_dialog_alert);
				builder.setNegativeButton(android.R.string.cancel, null);
				builder.setPositiveButton(R.string.button_delete, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int whichButton) {
						mDeleteFrameOnExit = true;
						onBackPressed();
					}
				});
				AlertDialog alert = builder.create();
				alert.show();
				break;
		}
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent resultIntent) {
		switch (requestCode) {
			case MediaPhone.R_id_intent_picture_editor:
			case MediaPhone.R_id_intent_audio_editor:
			case MediaPhone.R_id_intent_text_editor:
			case MediaPhone.R_id_intent_narrative_player:
				// if we get RESULT_OK then a media component has been edited - reload our content
				if (resultCode == Activity.RESULT_OK) {
					// only load our existing frame here; changes are handled in onWindowFocusChanged
					String newInternalId = loadLastEditedFrame();
					if (newInternalId != null && newInternalId.equals(mFrameInternalId)) {
						loadFrameElements();
						mHasEditedMedia = true;
						setBackButtonIcons(FrameEditorActivity.this, R.id.button_finished_editing, 0, true);
					}

				} else if (resultCode == R.id.result_audio_ok_exit) {
					// no point reloading if we're going to exit
					// done this way (rather than reloading in this activity) so we get switching right/left animations
					onBackPressed();
				} else if (resultCode == R.id.result_audio_cancelled_exit) {
					onBackPressed();
				} else if (resultCode == R.id.result_narrative_deleted_exit) {
					onBackPressed();
				}
				break;

			default:
				super.onActivityResult(requestCode, resultCode, resultIntent);
		}
	}

	@Override
	public void onProgressChanged(SeekBar arg0, int progress, boolean arg2) {

		// @Haiyue
		// record set values of volume
		if (arg0.equals(audio1Progress)) {
			volume1 = audio1Progress.getProgress();
		} else if (arg0.equals(audio2Progress)) {
			volume2 = audio2Progress.getProgress();
		} else if (arg0.equals(audio3Progress)) {
			volume3 = audio3Progress.getProgress();
		}
		// @Haiyue
		// save volume information after pressing each audio button
		SharedPreferences inputPrefs = getSharedPreferences(mFrameInternalId, 0);
		Editor editor = inputPrefs.edit();
		editor.putInt("volume1", volume1);
		editor.putInt("volume2", volume2);
		editor.putInt("volume3", volume3);
		editor.commit();
	}

	@Override
	public void onStartTrackingTouch(SeekBar arg0) {
		// TODO Auto-generated method stub
	}

	@Override
	public void onStopTrackingTouch(SeekBar arg0) {
		// TODO Auto-generated method stub
	}
}
