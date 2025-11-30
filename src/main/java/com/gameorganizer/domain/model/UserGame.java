package com.gameorganizer.domain.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.data.relational.core.mapping.Column;

@Table("user_games")
public class UserGame {
    @Id
    private Long id;

    @Column("rawg_id")
    private Integer rawgId;

    private String title;

    @Column("image_url")
    private String imageUrl;

    @Column("game_status")
    private String gameStatus;

    @Column("rating")
    private Integer rating;

    @Column("genres")
    private String genres;

    public UserGame() {}
    public String getGenres() { return genres; }
    public void setGenres(String genres) { this.genres = genres; }
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Integer getRawgId() { return rawgId; }
    public void setRawgId(Integer rawgId) { this.rawgId = rawgId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public String getGameStatus() { return gameStatus; }
    public void setGameStatus(String gameStatus) { this.gameStatus = gameStatus; }
    public Integer getRating() { return rating; }
    public void setRating(Integer rating) { this.rating = rating; }
}