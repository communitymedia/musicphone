/*
 *  Copyright (C) 2014 Haiyue Yuan
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
import java.util.List;

import ac.robinson.musicphone.R;
import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;

public class DropboxSpinnerActivity extends Activity {

	private Spinner spinner2;
	private Button btnSubmit;
	private String[] fnames = null;
	public static String DropBoxDirectory = null;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.dropbox_spinner);

		addItemsOnSpinner2();
		addListenerOnButton();
	}

	public void addItemsOnSpinner2() {
		// pass names of folders in Dropbox
		Bundle bundle = getIntent().getExtras();
		fnames = bundle.getStringArray("folder_name");
		spinner2 = (Spinner) findViewById(R.id.spinner2);
		List<String> list = new ArrayList<String>();
		int folder_length = fnames.length;
		Log.i("length is ", String.valueOf(folder_length));

		for (int i = 0; i < folder_length; i++) {
			list.add(fnames[i]);
		}

		ArrayAdapter<String> dataAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, list);
		dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinner2.setAdapter(dataAdapter);
	}

	public void addListenerOnButton() {

		spinner2 = (Spinner) findViewById(R.id.spinner2);
		btnSubmit = (Button) findViewById(R.id.btnSubmit);

		btnSubmit.setOnClickListener(new OnClickListener() {

			public void onClick(View v) {
				// remember the name of chosen folder
				DropBoxDirectory = String.valueOf(spinner2.getSelectedItem());
				onBackPressed();
			}

		});

	}

	public void onBackPressed() {
		super.onBackPressed();
	}
}
