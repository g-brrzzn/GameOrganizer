package com.gameorganizer.infra.mock;

import com.gameorganizer.api.dto.GameResponseDto;
import com.gameorganizer.domain.model.Game;
import com.gameorganizer.domain.port.GameEnrichmentPort;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Profile("mock-enrichment")
public class MockGameEnrichmentAdapter implements GameEnrichmentPort {

    @Override
    public Mono<GameResponseDto> enrich(GameResponseDto overview, Game game) {
        System.out.println("--- USANDO MODO MOCK PARA ENRIQUECIMENTO (STEAM + HLTB) ---");

        // How Long to Beat
        overview.setHltbMain("50h (Mock)");
        overview.setHltbCompletionist("172h (Mock)");
        overview.setHltbUrl("https://howlongtobeat.com/mock");

        // Steam
        overview.setSteamUrl("https://store.steampowered.com/app/mock");

        return Mono.just(overview);
    }
}