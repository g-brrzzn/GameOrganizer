package com.gameorganizer.service.hltb;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HowLongToBeat scraper melhorado:
 * - usa cache (Caffeine)
 * - extrai apenas pequenos fragmentos de texto (Main / Completionist) ao invés de Dump de todo HTML
 * - tenta múltiplos endpoints como fallback, com ignoreHttpErrors(true)
 *
 * Observação: scraping continua frágil e pode precisar ajuste de seletores conforme o site mudar.
 */
public class HowLongToBeatService {

    private static final String BASE = "https://howlongtobeat.com";
    private static final int TIMEOUT = 10_000;
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64)";

    // Cache simples: chave = termo de busca original (lowercase trim), valor = Optional<HLTBInfo>
    private final Cache<String, Optional<HLTBInfo>> cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofHours(24))
            .maximumSize(5_000)
            .build();

    // Regex para localizar tempos curtos no HTML (procura por padrões como "25 Hours", "53½ Hours", "25h", "25 hr")
    private static final Pattern TIME_PATTERN = Pattern.compile("(?i)(\\d{1,3}(?:[\\.,]\\d+)?(?:½)?\\s*(?:hours|hour|hrs|hr|h))");

    /**
     * Busca HLTB info — usa cache internamente.
     */
    public Optional<HLTBInfo> search(String gameTitle) {
        if (gameTitle == null || gameTitle.isBlank()) return Optional.empty();
        String key = gameTitle.trim().toLowerCase();

        // Consulta cache
        Optional<HLTBInfo> cached = cache.getIfPresent(key);
        if (cached != null) return cached;

        Optional<HLTBInfo> result = fetchAndParse(gameTitle);
        cache.put(key, result);
        return result;
    }

    private Optional<HLTBInfo> fetchAndParse(String gameTitle) {
        try {
            String encoded = URLEncoder.encode(gameTitle, StandardCharsets.UTF_8);

            List<String> candidates = List.of(
                    BASE + "/search_results?page=1&searchterm=" + encoded,
                    BASE + "/search_results?page=1&searchterm=" + encoded.replace("+", "%20"),
                    BASE + "/?q=" + encoded,
                    BASE + "/search?q=" + encoded
            );

            Document doc = null;
            Connection.Response lastResp = null;

            for (String url : candidates) {
                try {
                    Connection conn = Jsoup.connect(url)
                            .userAgent(USER_AGENT)
                            .referrer("https://google.com")
                            .timeout(TIMEOUT)
                            .ignoreHttpErrors(true)
                            .followRedirects(true);

                    Connection.Response resp = conn.execute();
                    lastResp = resp;
                    if (resp.statusCode() == 200) {
                        doc = resp.parse();
                        break;
                    }
                } catch (Exception ignore) {
                    // tenta próximo candidato
                }
            }

            if (doc == null) return Optional.empty();

            // Tentativa 1: encontrar o primeiro resultado (search results) e pegar link de detalhe
            Element firstResult = null;
            Elements results = doc.select(".search_list_details, .search_list_box");
            if (!results.isEmpty()) firstResult = results.first();

            Document detailDoc = doc;
            String nameFound = null;
            if (firstResult != null) {
                Element link = firstResult.selectFirst("a[href]");
                if (link != null) {
                    nameFound = link.text().trim();
                    String href = link.attr("href");
                    String detailUrl = href.startsWith("http") ? href : BASE + href;
                    try {
                        Connection.Response r2 = Jsoup.connect(detailUrl)
                                .userAgent(USER_AGENT)
                                .referrer(BASE)
                                .timeout(TIMEOUT)
                                .ignoreHttpErrors(true)
                                .followRedirects(true)
                                .execute();
                        if (r2.statusCode() == 200) detailDoc = r2.parse();
                    } catch (Exception ignore) { /* fallback para doc anterior */ }
                } else {
                    nameFound = firstResult.text().trim();
                }
            } else {
                // Se não houver resultados listados, estamos possivelmente numa página de detalhe já.
                Element titleEl = doc.selectFirst("h1, .profile_title, .game_title");
                if (titleEl != null) nameFound = titleEl.text().trim();
            }

            // Procurar área reduzida do conteúdo (evitar footer/sidebars)
            Element mainArea = firstNonNull(
                    detailDoc.selectFirst("#main_content"),
                    detailDoc.selectFirst(".game_page"),
                    detailDoc.selectFirst(".profile"),
                    detailDoc.selectFirst(".content"),
                    detailDoc.selectFirst("body")
            );

            if (mainArea == null) mainArea = detailDoc.body();

            // Extração dirigida: procurar por nodes curtos que contenham o label, e extrair nearby time
            String mainTime = extractTimeByLabel(mainArea, "Main Story");
            if (mainTime == null) mainTime = extractTimeByLabel(mainArea, "Main");

            String completionistTime = extractTimeByLabel(mainArea, "Completionist");

            // Se ainda não encontrou, procurar por padrões TIME_PATTERN dentro de fragmentos menores
            if (mainTime == null || completionistTime == null) {
                // procurar por elementos de texto curtos (não toda a mainArea.text())
                Elements candidatesEls = mainArea.select("div, span, p, li");
                for (Element el : candidatesEls) {
                    String text = el.ownText(); // ownText() evita concatenar texto de filhos grandes
                    if (text == null || text.isBlank()) continue;
                    String lower = text.toLowerCase();
                    if (mainTime == null && (lower.contains("main story") || lower.matches(".*\\bmain\\b.*"))) {
                        String t = findTimeInString(text);
                        if (t != null) mainTime = t;
                    }
                    if (completionistTime == null && lower.contains("completionist")) {
                        String t = findTimeInString(text);
                        if (t != null) completionistTime = t;
                    }
                    if (mainTime != null && completionistTime != null) break;
                }
            }

            // Fallback final: aplicar regex no HTML pequeno (somente no mainArea.html() truncado)
            if ((mainTime == null || completionistTime == null)) {
                String smallHtml = mainArea.html();
                // não aplicar em todo site; limitar a primeiros 20k chars
                if (smallHtml.length() > 20_000) smallHtml = smallHtml.substring(0, 20_000);
                Matcher m = TIME_PATTERN.matcher(smallHtml);
                if (m.find() && mainTime == null) mainTime = m.group(1);
                if (m.find() && completionistTime == null) completionistTime = m.group(1);
            }

            HLTBInfo info = new HLTBInfo();
            info.setName(nameFound);
            info.setMain(mainTime);
            info.setCompletionist(completionistTime);
            // baseUri ou lastResp
            String foundUrl = null;
            try { foundUrl = detailDoc.baseUri(); } catch (Exception ignored) {}
            if (foundUrl == null && lastResp != null) {
                try { foundUrl = lastResp.url().toString(); } catch (Exception ignored) {}
            }
            info.setUrl(foundUrl);

            // Normalizar: se strings tiverem muito conteúdo (sinal de parsing errado), truncar a ~200 chars
            if (info.getMain() != null && info.getMain().length() > 300) info.setMain(info.getMain().substring(0, 200).trim());
            if (info.getCompletionist() != null && info.getCompletionist().length() > 300) info.setCompletionist(info.getCompletionist().substring(0, 200).trim());

            return Optional.of(info);

        } catch (Exception e) {
            // erro geral -> vazio
            return Optional.empty();
        }
    }

    // procura por elementos contendo o label (case-insensitive) e tenta extrair tempo do sibling/parent
    private String extractTimeByLabel(Element root, String label) {
        if (label == null || root == null) return null;
        String css = String.format("*:matchesOwn((?i)%s)", Pattern.quote(label)); // matchesOwn para textos curtos
        Elements els = root.select(css);
        for (Element el : els) {
            // 1) procurar próximo sibling com texto curto que contenha número/hours
            Element sib = el.nextElementSibling();
            if (sib != null) {
                String t = findTimeInString(sib.ownText());
                if (t != null) return t;
            }
            // 2) procurar dentro do parent
            Element parent = el.parent();
            if (parent != null) {
                // procurar por elementos pequenos que contenham time pattern
                Elements candidates = parent.select("span, div, p, li");
                for (Element c : candidates) {
                    String t = findTimeInString(c.ownText());
                    if (t != null) return t;
                }
            }
            // 3) procurar texto próximo (ownText() para evitar juntar tudo)
            String t = findTimeInString(el.ownText());
            if (t != null) return t;
        }
        return null;
    }

    // tenta achar uma substring que case com TIME_PATTERN na string (retorna primeira ocorrência)
    private String findTimeInString(String s) {
        if (s == null || s.isBlank()) return null;
        Matcher m = TIME_PATTERN.matcher(s);
        if (m.find()) {
            return m.group(1).trim();
        }
        return null;
    }

    @SafeVarargs
    private static <T> T firstNonNull(T... items) {
        for (T t : items) if (t != null) return t;
        return null;
    }
}
