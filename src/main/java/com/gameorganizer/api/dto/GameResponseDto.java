package com.gameorganizer.api.dto;

import com.gameorganizer.infra.client.steam.dto.SteamAppDetails;

import java.util.List;

public class GameResponseDto {
    private String name;
    private Integer rawgId;
    private Integer playtime;
    private List<String> genres;
    private Integer steamAppid;
    private SteamAppDetails steamData;
    private String backgroundImage;

    // novos campos HLTB
    private String hltbMain;
    private String hltbCompletionist;
    private String hltbUrl;
    private String steamUrl;

    // getters / setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Integer getRawgId() { return rawgId; }
    public void setRawgId(Integer rawgId) { this.rawgId = rawgId; }
    public Integer getPlaytime() { return playtime; }
    public void setPlaytime(Integer playtime) { this.playtime = playtime; }
    public List<String> getGenres() { return genres; }
    public void setGenres(List<String> genres) { this.genres = genres; }
    public Integer getSteamAppid() { return steamAppid; }
    public void setSteamAppid(Integer steamAppid) { this.steamAppid = steamAppid; }
    public SteamAppDetails getSteamData() { return steamData; }
    public void setSteamData(SteamAppDetails steamData) { this.steamData = steamData; }
    public String getBackgroundImage() { return backgroundImage; }
    public void setBackgroundImage(String backgroundImage) { this.backgroundImage = backgroundImage; }

    public String getHltbMain() { return hltbMain; }
    public void setHltbMain(String hltbMain) { this.hltbMain = hltbMain; }
    public String getHltbCompletionist() { return hltbCompletionist; }
    public void setHltbCompletionist(String hltbCompletionist) { this.hltbCompletionist = hltbCompletionist; }
    public String getHltbUrl() { return hltbUrl; }
    public void setHltbUrl(String hltbUrl) { this.hltbUrl = hltbUrl; }
    public String getSteamUrl() { return steamUrl; }
    public void setSteamUrl(String steamUrl) { this.steamUrl = steamUrl; }
}
