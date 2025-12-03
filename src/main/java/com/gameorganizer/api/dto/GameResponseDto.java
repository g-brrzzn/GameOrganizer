package com.gameorganizer.api.dto;

import com.gameorganizer.infra.client.steam.dto.SteamAppDetails;
import java.util.List;

public class GameResponseDto {
    private String name;
    private Integer rawgId;
    private Integer metacritic;
    private String releaseYear;
    private List<String> genres;
    private Integer steamAppid;
    private SteamAppDetails steamData;
    private String backgroundImage;
    private String steamUrl;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Integer getRawgId() { return rawgId; }
    public void setRawgId(Integer rawgId) { this.rawgId = rawgId; }
    public Integer getMetacritic() { return metacritic; }
    public void setMetacritic(Integer metacritic) { this.metacritic = metacritic; }
    public String getReleaseYear() { return releaseYear; }
    public void setReleaseYear(String releaseYear) { this.releaseYear = releaseYear; }
    public List<String> getGenres() { return genres; }
    public void setGenres(List<String> genres) { this.genres = genres; }
    public Integer getSteamAppid() { return steamAppid; }
    public void setSteamAppid(Integer steamAppid) { this.steamAppid = steamAppid; }
    public SteamAppDetails getSteamData() { return steamData; }
    public void setSteamData(SteamAppDetails steamData) { this.steamData = steamData; }
    public String getBackgroundImage() { return backgroundImage; }
    public void setBackgroundImage(String backgroundImage) { this.backgroundImage = backgroundImage; }
    public String getSteamUrl() { return steamUrl; }
    public void setSteamUrl(String steamUrl) { this.steamUrl = steamUrl; }
}