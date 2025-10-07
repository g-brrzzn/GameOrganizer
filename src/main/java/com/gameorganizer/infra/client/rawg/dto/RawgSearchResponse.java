package com.gameorganizer.infra.client.rawg.dto;

import java.util.List;

public class RawgSearchResponse {
    public int count;
    public String next;
    public String previous;
    public List<RawgResult> results;
}
