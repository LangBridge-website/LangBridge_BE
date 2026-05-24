package com.project.Transflow.publish.service;

import com.project.Transflow.category.entity.Category;
import com.project.Transflow.category.repository.CategoryRepository;
import com.project.Transflow.publish.config.CreationKrProperties;
import com.project.Transflow.publish.dto.CreationKrBoardListResponse;
import com.project.Transflow.publish.dto.CreationKrBoardOption;
import com.project.Transflow.publish.service.CreationKrCategoryResolver.SitePathBoard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CreationKrBoardCatalogService {

    private final CategoryRepository categoryRepository;
    private final CreationKrProperties properties;
    private final CreationKrCategoryResolver categoryResolver;

    @Transactional(readOnly = true)
    public CreationKrBoardListResponse listBoards(Long categoryId) {
        Map<String, CreationKrBoardOption> bySitePath = new LinkedHashMap<>();

        for (Category category : categoryRepository.findAll()) {
            categoryResolver.resolveFromCategory(category).ifPresent(mapping -> {
                if (mapping.hasBoardId()) {
                    String label = buildLabel(category.getName(), mapping.getSitePath());
                    bySitePath.putIfAbsent(
                            mapping.getSitePath(),
                            CreationKrBoardOption.builder()
                                    .sitePath(mapping.getSitePath())
                                    .boardId(mapping.getBoardId())
                                    .label(label)
                                    .majorCategory(extractMajorCategory(label))
                                    .source("CATEGORY")
                                    .build()
                    );
                }
            });
        }

        if (properties.getBoardMappings() != null) {
            for (Map.Entry<String, String> entry : properties.getBoardMappings().entrySet()) {
                String sitePath = entry.getKey();
                String boardId = entry.getValue();
                if (sitePath == null || sitePath.isBlank() || boardId == null || boardId.isBlank()) {
                    continue;
                }
                String trimmedPath = sitePath.trim();
                String label = properties.resolveBoardLabel(trimmedPath);
                bySitePath.putIfAbsent(
                        trimmedPath,
                        CreationKrBoardOption.builder()
                                .sitePath(trimmedPath)
                                .boardId(boardId.trim())
                                .label(label)
                                .majorCategory(extractMajorCategory(label))
                                .source("CONFIG")
                                .build()
                );
            }
        }

        List<CreationKrBoardOption> boards = new ArrayList<>(bySitePath.values());
        boards.sort(Comparator
                .comparing(
                        (CreationKrBoardOption b) -> b.getMajorCategory() != null ? b.getMajorCategory() : "",
                        String.CASE_INSENSITIVE_ORDER
                )
                .thenComparing(CreationKrBoardOption::getLabel, String.CASE_INSENSITIVE_ORDER));

        Optional<SitePathBoard> suggested = categoryId != null
                ? categoryResolver.resolve(categoryId)
                : Optional.empty();

        CreationKrBoardListResponse.CreationKrBoardListResponseBuilder builder = CreationKrBoardListResponse.builder()
                .boards(boards);

        if (suggested.isPresent() && suggested.get().hasBoardId()) {
            SitePathBoard mapping = suggested.get();
            builder.suggestedSitePath(mapping.getSitePath())
                    .suggestedBoardId(mapping.getBoardId())
                    .suggestedLabel(findLabel(boards, mapping.getSitePath()));
        }

        return builder.build();
    }

    public boolean isValidBoard(String sitePath, String boardId) {
        if (sitePath == null || sitePath.isBlank() || boardId == null || boardId.isBlank()) {
            return false;
        }
        CreationKrBoardListResponse all = listBoards(null);
        return all.getBoards().stream()
                .anyMatch(b -> sitePath.trim().equals(b.getSitePath()) && boardId.trim().equals(b.getBoardId()));
    }

    private String findLabel(List<CreationKrBoardOption> boards, String sitePath) {
        return boards.stream()
                .filter(b -> sitePath.equals(b.getSitePath()))
                .map(CreationKrBoardOption::getLabel)
                .findFirst()
                .orElse(properties.resolveBoardLabel(sitePath));
    }

    private String buildLabel(String categoryName, String sitePath) {
        if (categoryName != null && !categoryName.isBlank()) {
            return categoryName.trim();
        }
        return properties.resolveBoardLabel(sitePath);
    }

    static String extractMajorCategory(String label) {
        if (label == null || label.isBlank()) {
            return null;
        }
        int i = label.indexOf('-');
        if (i > 0) {
            return label.substring(0, i).trim();
        }
        return label.trim();
    }
}
