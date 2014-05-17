/*
 *  @Haiyue Yuan 2014
 *  Create spinner to choose DropBox folder
 *  
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
