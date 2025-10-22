package com.gameorganizer.infra.client.howlongtobeat.dto;


public class HLTBInfo {
    private String name;
    private String main;
    private String completionist;
    private String url;

    public HLTBInfo() {}

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getMain() { return main; }
    public void setMain(String main) { this.main = main; }

    public String getCompletionist() { return completionist; }
    public void setCompletionist(String completionist) { this.completionist = completionist; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
}
