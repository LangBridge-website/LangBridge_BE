package com.project.Transflow.publish.service;

import com.project.Transflow.category.entity.Category;
import com.project.Transflow.category.repository.CategoryRepository;
import com.project.Transflow.publish.config.CreationKrProperties;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class CreationKrCategoryResolver {

    private final CategoryRepository categoryRepository;
    private final CreationKrProperties properties;

    public Optional<SitePathBoard> resolve(Long categoryId) {
        if (categoryId == null) {
            return Optional.empty();
        }
        return categoryRepository.findById(categoryId).flatMap(this::resolveFromCategory);
    }

    private Optional<SitePathBoard> resolveFromCategory(Category category) {
        if (category.getCreationKrSitePath() != null && !category.getCreationKrSitePath().isBlank()) {
            String sitePath = category.getCreationKrSitePath().trim();
            String boardId = category.getCreationKrBoardId();
            if (boardId == null || boardId.isBlank()) {
                boardId = properties.resolveBoardId(sitePath);
            }
            return Optional.of(new SitePathBoard(sitePath, boardId));
        }

        if (category.getCode() != null && !category.getCode().isBlank()) {
            String code = category.getCode().trim();
            String boardId = properties.resolveBoardId(code);
            if (boardId != null) {
                return Optional.of(new SitePathBoard(code, boardId));
            }
        }

        String minorFromName = extractMinorCategoryName(category.getName());
        if (minorFromName != null) {
            String boardId = properties.resolveBoardId(minorFromName);
            if (boardId != null) {
                return Optional.of(new SitePathBoard(minorFromName, boardId));
            }
        }

        return Optional.empty();
    }

    static String extractMinorCategoryName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        int i = name.indexOf('-');
        if (i < 0 || i >= name.length() - 1) {
            return null;
        }
        return name.substring(i + 1).trim();
    }

    @Getter
    public static class SitePathBoard {
        private final String sitePath;
        private final String boardId;

        public SitePathBoard(String sitePath, String boardId) {
            this.sitePath = sitePath;
            this.boardId = boardId;
        }

        public boolean hasBoardId() {
            return boardId != null && !boardId.isBlank();
        }
    }
}
