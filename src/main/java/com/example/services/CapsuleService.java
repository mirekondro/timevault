package com.example.services;

import com.example.entities.DailyCapsule;
import com.example.repositories.CapsuleRepository;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class CapsuleService {

    private static final List<String> FEED_URLS = List.of(
            "https://www.dr.dk/nyheder/service/feeds/senestenyt",
            "https://politiken.dk/rss/senestenyt.rss"
    );
    private static final Set<String> STOP_WORDS = Set.of(
            "the", "and", "for", "with", "that", "this", "from", "have", "today", "siger", "ikke",
            "der", "med", "som", "til", "fra", "over", "under", "mere", "mindre"
    );

    private final HttpClient httpClient;
    private final CapsuleRepository capsuleRepository;
    private final AiContextService aiContextService;

    public CapsuleService(HttpClient httpClient, CapsuleRepository capsuleRepository, AiContextService aiContextService) {
        this.httpClient = httpClient;
        this.capsuleRepository = capsuleRepository;
        this.aiContextService = aiContextService;
    }

    public DailyCapsule captureToday() {
        LocalDate today = LocalDate.now();
        List<String> headlines = fetchHeadlines();
        if (headlines.isEmpty()) {
            throw new IllegalStateException("No Danish headlines could be fetched right now.");
        }

        List<String> topics = extractTopics(headlines);
        String headline = headlines.stream().limit(2).collect(Collectors.joining(" / "));
        String vibeSummary = aiContextService.generateDailyVibe(today, headlines, topics);

        DailyCapsule capsule = new DailyCapsule(
                null,
                today,
                headline,
                vibeSummary,
                String.join(", ", topics),
                LocalDateTime.now()
        );
        return capsuleRepository.saveOrUpdate(capsule);
    }

    public List<DailyCapsule> listCapsules() {
        return capsuleRepository.findAll();
    }

    private List<String> fetchHeadlines() {
        List<String> headlines = new ArrayList<>();
        for (String feedUrl : FEED_URLS) {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(feedUrl))
                        .GET()
                        .header("User-Agent", "TimeVault/1.0")
                        .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 400) {
                    continue;
                }
                headlines.addAll(parseHeadlines(response.body()));
            } catch (Exception ignored) {
            }
        }
        return headlines.stream().distinct().limit(10).toList();
    }

    private List<String> parseHeadlines(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        Document document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        NodeList items = document.getElementsByTagName("item");

        List<String> headlines = new ArrayList<>();
        for (int index = 0; index < items.getLength(); index++) {
            Element item = (Element) items.item(index);
            String title = textContent(item, "title");
            if (title != null && !title.isBlank()) {
                headlines.add(title.trim());
            }
        }
        return headlines;
    }

    private List<String> extractTopics(List<String> headlines) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String headline : headlines) {
            for (String token : headline.toLowerCase(Locale.ENGLISH).split("[^a-z0-9æøå]+")) {
                if (token.length() < 4 || STOP_WORDS.contains(token)) {
                    continue;
                }
                counts.merge(token, 1L, Long::sum);
            }
        }
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry::getKey))
                .limit(6)
                .map(Map.Entry::getKey)
                .toList();
    }

    private String textContent(Element element, String tagName) {
        NodeList nodes = element.getElementsByTagName(tagName);
        if (nodes.getLength() == 0) {
            return null;
        }
        return nodes.item(0).getTextContent();
    }
}
