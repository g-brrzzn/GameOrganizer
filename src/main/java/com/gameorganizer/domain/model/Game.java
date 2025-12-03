package com.gameorganizer.domain.model;

import java.util.List;

public record Game(
        Integer id,
        String name,
        Integer metacritic,
        String releaseYear,
        String backgroundImage,
        List<String> genres,
        String steamUrl,
        Integer steamAppId
) {}