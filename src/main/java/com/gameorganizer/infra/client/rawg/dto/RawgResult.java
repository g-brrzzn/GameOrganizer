package com.gameorganizer.infra.client.rawg.dto;

import java.util.ArrayList;
import java.util.List;

public class RawgResult {
    public Integer id;
    public String name;
    public Integer metacritic;
    public String released;
    public List<RawgGenre> genres;
    public List<RawgStore> stores;
    public String background_image;

    public List<String> getGenreNames() {
        if (genres == null) return new ArrayList<>();
        List<String> out = new ArrayList<>();
        for (RawgGenre g : genres) out.add(g.name);
        return out;
    }
}