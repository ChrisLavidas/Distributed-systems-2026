package com.funGames.app.util;

import com.funGames.app.model.Game;
import java.util.ArrayList;
import java.util.List;

/**
 * In-memory holder to pass the last SEARCH result list and the filter
 * summary between MainActivity (filters) and ResultsActivity (game list).
 * Keeps things simple — no Parcelable threading.
 */
public class SearchResults {
    private static final SearchResults INSTANCE = new SearchResults();
    public static SearchResults get() { return INSTANCE; }

    private final List<Game> games = new ArrayList<>();
    private String filterSummary = "";

    public void set(List<Game> list, String summary) {
        games.clear();
        if (list != null) games.addAll(list);
        filterSummary = summary == null ? "" : summary;
    }

    public List<Game> getGames()       { return new ArrayList<>(games); }
    public String     getSummary()     { return filterSummary; }
    public int        size()           { return games.size(); }
}
