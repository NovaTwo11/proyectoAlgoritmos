package co.edu.uniquindio.proyectoAlgoritmos.service.viz;

import co.edu.uniquindio.proyectoAlgoritmos.model.dto.ArticleDTO;
import co.edu.uniquindio.proyectoAlgoritmos.model.dto.TimelineResponse;
import co.edu.uniquindio.proyectoAlgoritmos.service.articles.ArticlesService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TimelineVizService {

    private final ArticlesService articlesService;

    public ResponseEntity<TimelineResponse> buildTimeline() {
        List<ArticleDTO> articles = articlesService.getArticles().getBody();
        if (articles == null) articles = List.of();
        if (articles.isEmpty()) {
            return ResponseEntity.ok(TimelineResponse.builder()
                    .publicationsByYear(Map.of())
                    .publicationsByYearAndVenue(Map.of())
                    .venues(List.of())
                    .build());
        }
        Map<Integer,Integer> byYear = new TreeMap<>();
        Map<Integer, Map<String,Integer>> byYearVenue = new TreeMap<>();
        Set<String> venuesSet = new TreeSet<>();

        for (ArticleDTO a : articles) {
            Integer year = a.getYear();
            if (year == null) continue; // ignorar sin año para línea temporal
            String venue = chooseVenue(a);
            venuesSet.add(venue);
            byYear.merge(year, 1, Integer::sum);
            byYearVenue.computeIfAbsent(year, y -> new TreeMap<>()).merge(venue, 1, Integer::sum);
        }

        TimelineResponse resp = TimelineResponse.builder()
                .publicationsByYear(byYear)
                .publicationsByYearAndVenue(byYearVenue)
                .venues(new ArrayList<>(venuesSet))
                .build();
        log.info("Timeline agregada: años={}, venues={}", byYear.size(), venuesSet.size());
        return ResponseEntity.ok(resp);
    }

    private String chooseVenue(ArticleDTO a) {
        if (a.getJournal() != null && !a.getJournal().isBlank()) return a.getJournal().trim();
        if (a.getBooktitle() != null && !a.getBooktitle().isBlank()) return a.getBooktitle().trim();
        return "Unknown Venue";
    }
}

