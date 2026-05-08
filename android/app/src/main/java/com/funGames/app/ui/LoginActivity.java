package com.funGames.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.splashscreen.SplashScreen;

import com.funGames.app.R;
import com.funGames.app.util.Session;
import com.funGames.app.util.Validators;
import com.funGames.app.util.SessionPrefs;
import com.funGames.app.util.SoundManager;

/**
 * Entry screen — user picks a role (PLAYER / MANAGER) and the relevant
 * fields are shown. In-memory session, no persistence.
 */
public class LoginActivity extends AppCompatActivity {

    private enum Role { PLAYER, MANAGER }

    private Role selectedRole = Role.PLAYER;

    private TextView chipPlayer, chipManager;
    private TextView lblUserId;
    private EditText etUserId, etBalance, etMasterHost, etMasterPort;
    private View     balanceRow;
    private Button   btnLogin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        chipPlayer   = findViewById(R.id.chipRolePlayer);
        chipManager  = findViewById(R.id.chipRoleManager);
        lblUserId    = findViewById(R.id.lblUserId);
        etUserId     = findViewById(R.id.etPlayerId);      // reused as userId
        etBalance    = findViewById(R.id.etInitialBalance);
        balanceRow   = findViewById(R.id.rowBalance);
        etMasterHost = findViewById(R.id.etMasterHost);
        etMasterPort = findViewById(R.id.etMasterPort);
        btnLogin     = findViewById(R.id.btnLogin);

        chipPlayer.setOnClickListener(v -> setRole(Role.PLAYER));
        chipManager.setOnClickListener(v -> setRole(Role.MANAGER));
        setRole(Role.PLAYER);

        btnLogin.setOnClickListener(v -> { SoundManager.get().playClick(); attemptLogin(); });
        prefillFromSaved();
    }


    private void prefillFromSaved() {
        if (!SessionPrefs.hasSaved(this)) return;
        etMasterHost.setText(SessionPrefs.getHost(this));
        etMasterPort.setText(String.valueOf(SessionPrefs.getPort(this)));
        String saved = SessionPrefs.getUserId(this);
        if (!saved.isEmpty()) etUserId.setText(saved);
        String role = SessionPrefs.getRole(this);
        if ("MANAGER".equals(role)) setRole(Role.MANAGER);
    }

    private void setRole(Role r) {
        selectedRole = r;
        chipPlayer.setSelected(r == Role.PLAYER);
        chipManager.setSelected(r == Role.MANAGER);

        if (r == Role.PLAYER) {
            lblUserId.setText(R.string.lbl_player_id);
            etUserId.setHint(R.string.hint_player_id);
            balanceRow.setVisibility(View.VISIBLE);
            btnLogin.setText(R.string.btn_login_player);
        } else {
            lblUserId.setText(R.string.lbl_manager_id);
            etUserId.setHint(R.string.hint_manager_id);
            balanceRow.setVisibility(View.GONE);
            btnLogin.setText(R.string.btn_login_manager);
        }
    }

    private void attemptLogin() {
        String userId   = etUserId.getText().toString().trim();
        String host     = etMasterHost.getText().toString().trim();
        String portStr  = etMasterPort.getText().toString().trim();

        if (selectedRole == Role.PLAYER) {
            if (!Validators.isValidPlayerId(userId)) {
                ShakeErrorView.shake(etUserId, getString(R.string.err_invalid_player_id));
                return;
            }
        } else {
            if (!Validators.isValidManagerId(userId)) {
                toast(getString(R.string.err_invalid_manager_id));
                return;
            }
        }

        if (host.isEmpty()) {
            toast(getString(R.string.err_invalid_host));
            return;
        }
        int port;
        try {
            port = Integer.parseInt(portStr);
            if (port < 1 || port > 65535) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            toast(getString(R.string.err_invalid_port));
            return;
        }

        if (selectedRole == Role.PLAYER) {
            Double balance = Validators.parseDecimal(etBalance.getText().toString());
            if (balance == null || balance < 0 || balance > 1_000_000) {
                toast(getString(R.string.err_invalid_balance));
                return;
            }
            Session.get().initPlayer(userId, balance, host, port, port + 10);
            SessionPrefs.save(this, userId, "PLAYER", host, port, balance);
            startActivity(new Intent(this, MainActivity.class));
        } else {
            Session.get().initManager(userId, host, port);
            SessionPrefs.save(this, userId, "MANAGER", host, port, 0);
            startActivity(new Intent(this, ManagerActivity.class));
        }
        finish();
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}
