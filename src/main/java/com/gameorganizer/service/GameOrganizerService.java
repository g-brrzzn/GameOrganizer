package com.gameorganizer.service;

import com.gameorganizer.service.hltb.HowLongToBeatService;
import com.gameorganizer.service.hltb.HLTBInfo;
import com.gameorganizer.service.model.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class GameOrganizerService {
    private final WebClient webClient;
    private final String rawgApiKey;
    private final Pattern steamAppPattern = Pattern.compile("store.steampowered.com/app/(\\d+)");

    // HLTB scraper
    private final HowLongToBeatService hltbService = new HowLongToBeatService();

    public GameOrganizerService(WebClient webClient, @Value("${rawg.api.key:}") String rawgApiKey) {
        this.webClient = webClient;
        this.rawgApiKey = rawgApiKey;
    }

    public List<GameOverview> organizeByName(String name) {
        List<RawgResult> results = searchRawg(name);
        List<GameOverview> out = new ArrayList<>();

        for (RawgResult r : results) {
            GameOverview g = new GameOverview();
            g.setName(r.name);
            g.setRawgId(r.id);
            g.setPlaytime(r.playtime);
            g.setGenres(r.getGenreNames());
            g.setBackgroundImage(r.background_image);

            if (r.stores != null) {
                for (RawgStore s : r.stores) {
                    if (s != null && s.store != null && s.store.name != null) {
                        if ("steam".equalsIgnoreCase(s.store.name) || "steam".equalsIgnoreCase(s.store.name.trim())) {
                            g.setSteamUrl(s.url); // link direto (por ex. https://store.steampowered.com/app/XXXX)
                            break;
                        }
                        // alguns RAWG usam nome com 'Steam' capitalizado ou 'Steam' — usar equalsIgnoreCase cobre isso
                        if ("Steam".equalsIgnoreCase(s.store.name)) {
                            g.setSteamUrl(s.url);
                            break;
                        }
                    }
                }
            }

            Integer appid = extractSteamAppId(r);
            if (appid != null) {
                g.setSteamAppid(appid);
                g.setSteamData(fetchSteamAppDetails(appid));
            }

            // HLTB: tentativa de obter tempos via scraping (bloqueante)
            try {
                Optional<HLTBInfo> h = hltbService.search(r.name);
                if (h.isPresent()) {
                    HLTBInfo info = h.get();
                    g.setHltbMain(info.getMain());
                    g.setHltbCompletionist(info.getCompletionist());
                    g.setHltbUrl(info.getUrl());
                }
            } catch (Exception e) {
                // se falhar, apenas continue; não quebre toda a lista
                e.printStackTrace();
            }

            out.add(g);
        }

        // ordenar por playtime (forçando o tipo da lambda)
        out.sort(Comparator.comparingInt((com.gameorganizer.service.model.GameOverview ga) ->
                ga.getPlaytime() == null ? 0 : ga.getPlaytime()).reversed());
        return out;
    }

    private List<RawgResult> searchRawg(String name) {
        try {
            String url = (rawgApiKey == null || rawgApiKey.isEmpty())
                    ? "https://api.rawg.io/api/games?search=" + urlEncode(name) + "&page_size=15"
                    : "https://api.rawg.io/api/games?key=" + urlEncode(rawgApiKey) + "&search=" + urlEncode(name) + "&page_size=15";

            RawgSearchResponse resp = webClient.get()
                    .uri(url)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(RawgSearchResponse.class)
                    .block();

            if (resp == null || resp.results == null) return Collections.emptyList();
            return resp.results;
        } catch (Exception e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    private SteamAppDetails fetchSteamAppDetails(int appid) {
        try {
            String url = "https://store.steampowered.com/api/appdetails?appids=" + appid + "&l=en&cc=us";
            Map map = webClient.get()
                    .uri(url)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
            if (map == null) return null;
            Object raw = map.get(String.valueOf(appid));
            SteamAppDetails s = new SteamAppDetails();
            s.setRaw(raw);
            return s;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private Integer extractSteamAppId(RawgResult r) {
        if (r.stores == null) return null;
        for (RawgStore s : r.stores) {
            if (s.url != null) {
                Matcher m = steamAppPattern.matcher(s.url);
                if (m.find()) {
                    try { return Integer.parseInt(m.group(1)); } catch (NumberFormatException ignored){}
                }
            }
        }
        return null;
    }

    private static String urlEncode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
