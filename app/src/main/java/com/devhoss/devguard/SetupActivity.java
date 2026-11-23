package com.devhoss.devguard;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

/** One-time ADB permission onboarding. */
public class SetupActivity extends Activity {

    private ImageView stateIcon;
    private TextView stateTitle;
    private TextView stateBody;

    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(ThemeHelper.wrap(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTheme(R.style.AppTheme);
        setContentView(R.layout.activity_setup);
        ThemeHelper.applySystemBarIcons(this);

        stateIcon = findViewById(R.id.setup_state_icon);
        stateTitle = findViewById(R.id.setup_state_title);
        stateBody = findViewById(R.id.setup_state_body);

        ((TextView) findViewById(R.id.setup_command)).setText(AdbSetup.command(this));

        findViewById(R.id.btn_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        View.OnClickListener copy = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (AdbSetup.copyCommand(SetupActivity.this)) {
                    Ui.toast(SetupActivity.this, getString(R.string.command_copied));
                }
            }
        };
        findViewById(R.id.btn_copy).setOnClickListener(copy);
        findViewById(R.id.btn_recheck).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (DevSettings.hasPermission(SetupActivity.this)) {
                    refreshState();
                    Ui.toast(SetupActivity.this, getString(R.string.setup_granted));
                } else {
                    Ui.toast(SetupActivity.this, getString(R.string.setup_recheck_failed));
                }
            }
        });

        refreshState();
    }

    @Override
    protected void onResume() {
        super.onResume();
        ThemeHelper.applySystemBarIcons(this);
        refreshState();
    }

    private void refreshState() {
        boolean granted = DevSettings.hasPermission(this);
        stateTitle.setText(granted ? R.string.setup_granted : R.string.setup_required);
        stateBody.setText(granted ? R.string.setup_granted_body : R.string.setup_required_body);
        stateIcon.setImageResource(granted ? R.drawable.ic_check : R.drawable.ic_warn);
        stateIcon.setImageTintList(ColorStateList.valueOf(
                getColor(granted ? R.color.accent_ink : R.color.warn_ink)));
    }
}
