package com.funGames.app.ui;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.*;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import androidx.appcompat.app.AppCompatActivity;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import com.funGames.app.R;
import com.funGames.app.net.MasterClient;
import com.funGames.app.util.Session;
import com.funGames.app.util.SoundManager;

public class ManagerActivity extends AppCompatActivity {

    private MasterClient client;

    private TextView tabGames, tabProvider, tabPlayer, tabSystem;
    private View     paneGames, paneProvider, panePlayer, paneSystem;

    // Stats outputs
    private TextView     tvOutputProvider, tvOutputPlayer, tvOutputLeaderboard,
                         tvOutputWorkerStatus, tvOutputStressTest;
    private StatsChartView chartProvider, chartPlayer;

    private TextView tvManagerId, tvConnInfo;
    private View     progress;

    // Search filter
    private EditText etSearch;
    private String   searchQuery = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!Session.get().isInitialized() || !Session.get().isManager()) {
            startActivity(new Intent(this, LoginActivity.class));
            finish(); return;
        }

        setContentView(R.layout.activity_manager);
        client = Session.get().getClient();

        // tabs
        tabGames    = findViewById(R.id.tabGames);
        tabProvider = findViewById(R.id.tabProvider);
        tabPlayer   = findViewById(R.id.tabPlayer);
        tabSystem   = findViewById(R.id.tabSystem);
        paneGames    = findViewById(R.id.paneGames);
        paneProvider = findViewById(R.id.paneProvider);
        panePlayer   = findViewById(R.id.panePlayer);
        paneSystem   = findViewById(R.id.paneSystem);

        tabGames   .setOnClickListener(v -> showTab(0));
        tabProvider.setOnClickListener(v -> showTab(1));
        tabPlayer  .setOnClickListener(v -> showTab(2));
        tabSystem  .setOnClickListener(v -> showTab(3));
        showTab(0);

        // Games tab
        findViewById(R.id.btnAddGame)   .setOnClickListener(v -> openAddGameDialog());
        findViewById(R.id.btnRemoveGame).setOnClickListener(v -> openRemoveGameDialog());
        findViewById(R.id.btnChangeRisk).setOnClickListener(v -> openChangeRiskDialog());



        // Stats
        tvOutputProvider    = findViewById(R.id.tvOutputProvider);
        tvOutputPlayer      = findViewById(R.id.tvOutputPlayer);
        tvOutputLeaderboard  = findViewById(R.id.tvOutputLeaderboard);
        tvOutputWorkerStatus = findViewById(R.id.tvOutputWorkerStatus);
        tvOutputStressTest   = findViewById(R.id.tvOutputStressTest);
        chartProvider    = findViewById(R.id.chartProvider);
        chartPlayer      = findViewById(R.id.chartPlayer);

        findViewById(R.id.btnRunProvider)   .setOnClickListener(v -> runProviderStats());
        findViewById(R.id.btnRunPlayer)     .setOnClickListener(v -> runPlayerStats());
        View btnLeaderboard = findViewById(R.id.btnRunLeaderboard);
        if (btnLeaderboard != null) btnLeaderboard.setOnClickListener(v -> runLeaderboard());
        View btnWorkerStatus = findViewById(R.id.btnWorkerStatus);
        if (btnWorkerStatus != null) btnWorkerStatus.setOnClickListener(v -> runWorkerStatus());
        View btnStressTest = findViewById(R.id.btnStressTest);
        if (btnStressTest != null) btnStressTest.setOnClickListener(v -> runStressTest());

        // Copy to clipboard buttons
        View btnCopyProv = findViewById(R.id.btnCopyProvider);
        if (btnCopyProv != null) btnCopyProv.setOnClickListener(v ->
                copyToClipboard("Provider Stats", tvOutputProvider.getText().toString()));
        View btnCopyPlay = findViewById(R.id.btnCopyPlayer);
        if (btnCopyPlay != null) btnCopyPlay.setOnClickListener(v ->
                copyToClipboard("Player Stats", tvOutputPlayer.getText().toString()));

        // System tab
        tvManagerId = findViewById(R.id.tvManagerId);
        tvConnInfo  = findViewById(R.id.tvConnInfo);
        String mgrDisplay = "👤 " + Session.get().getManagerId();
        String srvDisplay = Session.get().getMasterHost() + ":" + Session.get().getMasterPort();
        if (tvManagerId != null) tvManagerId.setText(mgrDisplay);
        if (tvConnInfo  != null) tvConnInfo.setText(srvDisplay);
        TextView tvMgrSys  = findViewById(R.id.tvManagerIdSystem);
        TextView tvConnSys = findViewById(R.id.tvConnInfoSystem);
        if (tvMgrSys  != null) tvMgrSys.setText(Session.get().getManagerId());
        if (tvConnSys != null) tvConnSys.setText(srvDisplay);
        findViewById(R.id.btnLogoutMgr)   .setOnClickListener(v -> logout());
        findViewById(R.id.btnLogoutMgrTop).setOnClickListener(v -> logout());
        progress = findViewById(R.id.progressMgr);

        // Connection check
        checkConnection();
        animateEntranceCards();
    }

    private void animateEntranceCards() {
        int[] ids = {R.id.btnAddGame, R.id.btnChangeRisk, R.id.btnRemoveGame};
        for (int i = 0; i < ids.length; i++) {
            View v = findViewById(ids[i]);
            if (v == null) continue;
            v.setAlpha(0f);
            v.setTranslationX(-60f);
            v.animate()
                .alpha(1f).translationX(0f)
                .setStartDelay(200 + i * 120L)
                .setDuration(400)
                .setInterpolator(new OvershootInterpolator(1.2f))
                .start();
        }
    }

    // ── Tab switching ─────────────────────────────────────────────────
    private void showTab(int idx) {
        SoundManager.get().playClick();
        TextView[] tabs  = {tabGames, tabProvider, tabPlayer, tabSystem};
        View[]     panes = {paneGames, paneProvider, panePlayer, paneSystem};
        for (int i = 0; i < 4; i++) {
            tabs[i].setSelected(i == idx);
            if (i == idx) {
                panes[i].setVisibility(View.VISIBLE);
                panes[i].setAlpha(0f);
                panes[i].setTranslationY(24f);
                panes[i].animate()
                    .alpha(1f).translationY(0f)
                    .setDuration(280)
                    .setInterpolator(new DecelerateInterpolator(2f))
                    .start();
                // animate children with stagger
                if (panes[i] instanceof android.view.ViewGroup) {
                    android.view.ViewGroup vg = (android.view.ViewGroup) panes[i];
                    // find first scrollview child
                    View inner = vg;
                    if (vg instanceof android.widget.ScrollView) {
                        inner = ((android.widget.ScrollView)vg).getChildAt(0);
                    }
                    if (inner instanceof android.view.ViewGroup) {
                        android.view.ViewGroup children = (android.view.ViewGroup) inner;
                        for (int c = 0; c < children.getChildCount(); c++) {
                            View child = children.getChildAt(c);
                            child.setAlpha(0f);
                            child.setTranslationY(32f);
                            child.animate()
                                .alpha(1f).translationY(0f)
                                .setStartDelay(60 + c * 70L)
                                .setDuration(300)
                                .setInterpolator(new DecelerateInterpolator(1.8f))
                                .start();
                        }
                    }
                }
            } else {
                panes[i].setVisibility(View.GONE);
            }
        }
    }

    // ── Add game ──────────────────────────────────────────────────────
    private void openAddGameDialog() {
        final String[] files;
        try { files = getAssets().list("games"); }
        catch (IOException e) { toast(getString(R.string.msg_error_prefix, e.getMessage())); return; }
        if (files == null || files.length == 0) { toast(getString(R.string.err_no_json_files)); return; }

        // Filter by search query
        List<String> filtered = new ArrayList<>();
        for (String f : files) if (f.toLowerCase().contains(searchQuery)) filtered.add(f);
        if (filtered.isEmpty()) filtered.addAll(Arrays.asList(files));
        final String[] fArr = filtered.toArray(new String[0]);

        String[] labels = new String[fArr.length];
        for (int i=0; i<fArr.length; i++) {
            String name = fArr[i];
            try (InputStream is = getAssets().open("games/"+fArr[i])) {
                byte[] buf=new byte[is.available()]; is.read(buf);
                String parsed = extractJsonString(new String(buf,"UTF-8"),"GameName");
                if (!parsed.isEmpty()) name = parsed + "  ("+fArr[i]+")";
            } catch (IOException ignored) {}
            labels[i] = name;
        }

        final int[] sel = {-1};
        new AlertDialog.Builder(this)
            .setTitle(R.string.dlg_select_game_title)
            .setSingleChoiceItems(labels,-1,(di,w)->sel[0]=w)
            .setPositiveButton(R.string.btn_confirm_add, (di,w)->{
                if (sel[0]<0){toast(getString(R.string.err_select_game));return;}
                try (InputStream is = getAssets().open("games/"+fArr[sel[0]])){
                    byte[] buf = readFully(is);
                    String json = new String(buf, java.nio.charset.StandardCharsets.UTF_8);
                    String transport=buildTransportFromJson(json);
                    String name=extractJsonString(json,"GameName");
                    setLoading(true);
                    client.addGameAsync(transport,(ok,payload)->{
                        setLoading(false);
                        showResult(ok?getString(R.string.msg_game_added,name)
                                     :getString(R.string.msg_error_prefix,payload));
                    });
                } catch (IOException e) {toast(getString(R.string.msg_error_prefix,e.getMessage()));}
            })
            .setNegativeButton(R.string.btn_cancel,null)
            .show();
    }

    // Remove game
    private void openRemoveGameDialog() {
        EditText input = new EditText(this);
        input.setHint(R.string.hint_game_name);
        input.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.text_primary));
        input.setHintTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.text_muted));
        input.setPadding(32,24,32,24);
        new AlertDialog.Builder(this)
            .setTitle(R.string.dlg_remove_title)
            .setView(input)
            .setPositiveButton(R.string.btn_remove, (di,w)->{
                String name=input.getText().toString().trim();
                if(name.isEmpty()){toast(getString(R.string.err_fields_required));return;}
                setLoading(true);
                client.removeGameAsync(name,(ok,payload)->{
                    setLoading(false);
                    showResult(ok?getString(R.string.msg_game_removed,name)
                                 :getString(R.string.msg_error_prefix,payload));
                });
            })
            .setNegativeButton(R.string.btn_cancel,null)
            .show();
    }

    // Change risk — visual selector
    private void openChangeRiskDialog() {
        final Dialog d = new Dialog(this);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        d.setContentView(R.layout.dialog_change_risk);
        Window w = d.getWindow();
        if (w!=null){
            w.setBackgroundDrawableResource(android.R.color.transparent);
            w.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.WRAP_CONTENT);
        }
        EditText etName = d.findViewById(R.id.etGameNameR);
        final String[] risk = {"low"};
        TextView rLow=d.findViewById(R.id.rkLow2),
                 rMed=d.findViewById(R.id.rkMed2),
                 rHigh=d.findViewById(R.id.rkHigh2);
        rLow.setSelected(true);
        View.OnClickListener rc = v -> {
            rLow.setSelected(v==rLow); rMed.setSelected(v==rMed); rHigh.setSelected(v==rHigh);
            if(v==rLow) risk[0]="low"; else if(v==rMed) risk[0]="medium"; else risk[0]="high";
            // Visual colour feedback
            rLow .setTextColor(v==rLow  ? 0xFF34D399 : 0xFF666688);
            rMed .setTextColor(v==rMed  ? 0xFFFBBF24 : 0xFF666688);
            rHigh.setTextColor(v==rHigh ? 0xFFF87171 : 0xFF666688);
        };
        rLow.setOnClickListener(rc); rMed.setOnClickListener(rc); rHigh.setOnClickListener(rc);
        rLow.setTextColor(0xFF34D399); rMed.setTextColor(0xFF666688); rHigh.setTextColor(0xFF666688);

        d.findViewById(R.id.btnCancelR).setOnClickListener(v->d.dismiss());
        d.findViewById(R.id.btnConfirmR).setOnClickListener(v->{
            String name=etName.getText().toString().trim();
            if(name.isEmpty()){toast(getString(R.string.err_fields_required));return;}
            setLoading(true); d.dismiss();
            client.updateRiskAsync(name,risk[0],(ok,payload)->{
                setLoading(false);
                showResult(ok?getString(R.string.msg_risk_updated,name,risk[0])
                             :getString(R.string.msg_error_prefix,payload));
            });
        });
        d.show();
    }

    // Provider stats + chart
    private void runProviderStats() {
        tvOutputProvider.setText(R.string.msg_running);
        if (chartProvider!=null) chartProvider.setVisibility(View.GONE);
        client.statsProviderAsync((ok,payload)->{
            if (ok) {
                String out = payload.isEmpty() ? getString(R.string.msg_no_stats) : payload;
                tvOutputProvider.setText(out);
                if (chartProvider!=null) {
                    chartProvider.setVisibility(View.VISIBLE);
                    chartProvider.setData(out);
                }
            } else {
                tvOutputProvider.setText(getString(R.string.msg_error_prefix,payload));
            }
        });
    }

    // Player stats + leaderboard
    private void runPlayerStats() {
        tvOutputPlayer.setText(R.string.msg_running);
        if (chartPlayer!=null) chartPlayer.setVisibility(View.GONE);
        client.statsPlayerAsync((ok,payload)->{
            if (ok) {
                String out = payload.isEmpty() ? getString(R.string.msg_no_stats) : payload;
                tvOutputPlayer.setText(out);
                if (chartPlayer!=null) {
                    chartPlayer.setVisibility(View.VISIBLE);
                    chartPlayer.setData(out);
                }
            } else {
                tvOutputPlayer.setText(getString(R.string.msg_error_prefix,payload));
            }
        });
    }

    // Leaderboard
    private void runLeaderboard() {
        if (tvOutputLeaderboard != null) tvOutputLeaderboard.setText("Running...");
        client.leaderboardAsync((ok, payload) -> {
            if (tvOutputLeaderboard == null) return;
            if (ok) {
                tvOutputLeaderboard.setText(payload.isEmpty() ? "No bets placed yet." : payload);
            } else {
                tvOutputLeaderboard.setText("Error: " + payload);
            }
        });
    }

    // Worker Status
    private void runWorkerStatus() {
        if (tvOutputWorkerStatus != null) tvOutputWorkerStatus.setText("Checking workers...");
        client.workerStatusAsync((ok, payload) -> {
            if (tvOutputWorkerStatus == null) return;
            tvOutputWorkerStatus.setText(ok ? payload : "Error: " + payload);
        });
    }

    // Stress Test
    private void runStressTest() {
        if (tvOutputStressTest != null) tvOutputStressTest.setText("Running 50 concurrent bets...");
        client.stressTestAsync(50, (ok, payload) -> {
            if (tvOutputStressTest == null) return;
            tvOutputStressTest.setText(ok ? payload : "Error: " + payload);
        });
    }

    // Copy to clipboard
    private void copyToClipboard(String label, String text) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText(label, text));
            Toast.makeText(this, "📋 Copied to clipboard", Toast.LENGTH_SHORT).show();
        }
    }

    // Connection status
    private void checkConnection() {
        ConnectionStatusView csv = findViewById(R.id.connStatusMgr);
        if (csv==null) return;
        csv.setConnecting();
        long t = System.currentTimeMillis();
        client.checkAnyGameAsync((ok,p)->{
            long ms = System.currentTimeMillis()-t;
            boolean connected = ok || !p.startsWith("Network error");
            if(connected) csv.setOnline(ms); else csv.setOffline();
        });
    }

    // Helpers
    private void setLoading(boolean on) { progress.setVisibility(on?View.VISIBLE:View.GONE); }

    private void showResult(String msg) {
        new AlertDialog.Builder(this)
            .setTitle(R.string.dlg_result_title).setMessage(msg)
            .setPositiveButton(R.string.btn_ok,null).show();
    }

    private void toast(String s) { Toast.makeText(this,s,Toast.LENGTH_SHORT).show(); }
    private void logout() { startActivity(new Intent(this,LoginActivity.class)); finish(); }

    /** Reads all bytes from an InputStream reliably. */
    private static byte[] readFully(java.io.InputStream is) throws java.io.IOException {
        java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int n;
        while ((n = is.read(chunk)) != -1) buf.write(chunk, 0, n);
        return buf.toByteArray();
    }

    private String buildTransportFromJson(String json) {
        String name=extractJsonString(json,"GameName"),prov=extractJsonString(json,"ProviderName");
        double stars=extractJsonDouble(json,"Stars"); int votes=(int)extractJsonDouble(json,"NoOfVotes");
        String logo=extractJsonString(json,"GameLogo");
        double minB=extractJsonDouble(json,"MinBet"),maxB=extractJsonDouble(json,"MaxBet");
        String risk=extractJsonString(json,"RiskLevel"),hash=extractJsonString(json,"HashKey");
        return name+"~~"+prov+"~~"+stars+"~~"+votes+"~~"+logo+"~~"+minB+"~~"+maxB+"~~"+risk+"~~"+hash+"~~true";
    }
    private String extractJsonString(String json,String key){
        int idx=json.indexOf("\""+key+"\""); if(idx<0)return"";
        int colon=json.indexOf(':',idx),q1=json.indexOf('"',colon+1),q2=json.indexOf('"',q1+1);
        return json.substring(q1+1,q2);
    }
    private double extractJsonDouble(String json,String key){
        int idx=json.indexOf("\""+key+"\""); if(idx<0)return 0;
        int pos=json.indexOf(':',idx)+1;
        while(pos<json.length()&&Character.isWhitespace(json.charAt(pos)))pos++;
        int end=pos;
        while(end<json.length()&&(Character.isDigit(json.charAt(end))||json.charAt(end)=='.'||json.charAt(end)=='-'))end++;
        try{return Double.parseDouble(json.substring(pos,end));}catch(Exception e){return 0;}
    }
}
