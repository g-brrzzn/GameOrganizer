package com.gameorganizer.service.hltb;

/**
 * DTO simples para carregar as infos extraídas do HowLongToBeat.
 */
public class HLTBInfo {
    private String name;
    private String main;           // tempo da campanha principal
    private String completionist;  // tempo para completar tudo
    private String url;            // link da página no HLTB

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
