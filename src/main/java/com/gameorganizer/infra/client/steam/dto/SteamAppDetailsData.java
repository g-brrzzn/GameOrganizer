package com.gameorganizer.infra.client.steam.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class SteamAppDetailsData {
    @JsonProperty("name")
    public String name;

    @JsonProperty("header_image")
    public String headerImage;

    @JsonProperty("short_description")
    public String shortDescription;
}