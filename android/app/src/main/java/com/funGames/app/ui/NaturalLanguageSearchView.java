package com.funGames.app.ui;

import android.content.Context;
import android.graphics.*;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.*;
import android.widget.*;

import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Natural language search bar.
 * Player types: "high risk games under 10 FUN"
 * Claude API extracts filters → calls back with parsed values.
 */
public class NaturalLanguageSearchView extends LinearLayout {

    public interface ParsedFilterListener {
        void onFilters(String risk, String betCat, String minStars);
    }

    private EditText    etQuery;
    private TextView    tvStatus;
    private View        btnAi;
    private ParsedFilterListener listener;
    private final Handler uiH = new Handler(Looper.getMainLooper());

    public NaturalLanguageSearchView(Context c) { super(c); build(); }
    public NaturalLanguageSearchView(Context c, AttributeSet a) { super(c,a); build(); }

    public void setListener(ParsedFilterListener l) { listener=l; }

    private void build() {
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setPadding(0,8,0,8);

        // Input
        etQuery = new EditText(getContext());
        etQuery.setHint("🤖 Try: \"high risk jackpot games\"");
        etQuery.setHintTextColor(0xFF444466);
        etQuery.setTextColor(0xFFFFFFFF);
        etQuery.setTextSize(12f);
        etQuery.setBackgroundColor(0xFF080A18);
        etQuery.setPadding(20,16,20,16);
        etQuery.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        etQuery.setOnEditorActionListener((v,a,e)->{
            if(a==android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH){ parseAndSearch(); return true; }
            return false;
        });
        LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(0,-2,1f);
        ep.setMargins(0,0,8,0);
        addView(etQuery, ep);

        // AI button
        btnAi = new TextView(getContext());
        ((TextView)btnAi).setText("✨");
        ((TextView)btnAi).setTextSize(20f);
        ((TextView)btnAi).setGravity(Gravity.CENTER);
        ((TextView)btnAi).setBackgroundColor(0xFF1E1560);
        btnAi.setPadding(20,0,20,0);
        btnAi.setOnClickListener(v->parseAndSearch());
        addView(btnAi, new LinearLayout.LayoutParams(100,100));

        // Status label (shown below in parent)
        tvStatus = new TextView(getContext());
        tvStatus.setTextColor(0xFF2DD4BF);
        tvStatus.setTextSize(11f);
        tvStatus.setPadding(4,0,0,0);
    }

    public TextView getStatusView() { return tvStatus; }

    private void parseAndSearch() {
        String q = etQuery.getText().toString().trim();
        if (q.isEmpty()) return;
        tvStatus.setText("🤖 Interpreting…");
        ((TextView)btnAi).setText("⏳");
        btnAi.setEnabled(false);

        new Thread(() -> {
            String result = askClaude(q);
            uiH.post(() -> {
                ((TextView)btnAi).setText("✨");
                btnAi.setEnabled(true);
                handleResult(result, q);
            });
        }).start();
    }

    private void handleResult(String json, String original) {
        try {
            JSONObject obj = new JSONObject(json);
            String risk    = obj.optString("risk",    "ANY");
            String betCat  = obj.optString("betCat",  "ANY");
            String minStars= obj.optString("minStars","0");
            tvStatus.setText("✅ Filters applied from: \"" + original + "\"");
            if (listener!=null) listener.onFilters(risk, betCat, minStars);
        } catch (Exception e) {
            tvStatus.setText("⚠️ Could not parse — using defaults");
            if (listener!=null) listener.onFilters("ANY","ANY","0");
        }
    }

    private String askClaude(String query) {
        try {
            URL url = new URL("https://api.anthropic.com/v1/messages");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type","application/json");
            conn.setRequestProperty("anthropic-version","2023-06-01");
            conn.setDoOutput(true);
            conn.setConnectTimeout(8000); conn.setReadTimeout(10000);

            String system = "You extract game search filters from natural language. " +
                "Return ONLY valid JSON with these fields: " +
                "risk (one of: low, medium, high, ANY), " +
                "betCat (one of: $, $$, $$$, ANY), " +
                "minStars (integer 0-5 as string). " +
                "Examples: " +
                "\"cheap low risk games\" → {\"risk\":\"low\",\"betCat\":\"$\",\"minStars\":\"0\"}, " +
                "\"high risk jackpot\" → {\"risk\":\"high\",\"betCat\":\"ANY\",\"minStars\":\"0\"}, " +
                "\"popular medium games\" → {\"risk\":\"medium\",\"betCat\":\"ANY\",\"minStars\":\"4\"}. " +
                "Output ONLY the JSON object, nothing else.";

            org.json.JSONObject body = new org.json.JSONObject();
            body.put("model","claude-sonnet-4-20250514");
            body.put("max_tokens",100);
            body.put("system",system);
            org.json.JSONArray msgs = new org.json.JSONArray();
            org.json.JSONObject m = new org.json.JSONObject();
            m.put("role","user"); m.put("content",query);
            msgs.put(m);
            body.put("messages",msgs);

            try(OutputStream os=conn.getOutputStream()){
                os.write(body.toString().getBytes("UTF-8"));
            }

            int status=conn.getResponseCode();
            InputStream is=(status<400)?conn.getInputStream():conn.getErrorStream();
            ByteArrayOutputStream baos=new ByteArrayOutputStream();
            byte[] buf=new byte[2048]; int n;
            while((n=is.read(buf))!=-1) baos.write(buf,0,n);
            String resp=baos.toString("UTF-8");

            org.json.JSONObject json=new org.json.JSONObject(resp);
            return json.getJSONArray("content").getJSONObject(0).getString("text");
        } catch(Exception e) {
            return "{\"risk\":\"ANY\",\"betCat\":\"ANY\",\"minStars\":\"0\"}";
        }
    }
}
