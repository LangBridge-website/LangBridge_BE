package com.project.Transflow.publish.service;

import com.project.Transflow.category.entity.Category;
import com.project.Transflow.category.repository.CategoryRepository;
import com.project.Transflow.publish.config.CreationKrProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

/**
 * application.yml board-mappings / board-labels 기준으로 LangBridge Category를 시드합니다.
 * code=sitePath, name=board-label, creationKrSitePath/boardId 동기화.
 */
@Slf4j
@Component
@Order(100)
@RequiredArgsConstructor
public class CreationKrCategorySeedService implements ApplicationRunner {

    private final CreationKrProperties properties;
    private final CategoryRepository categoryRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!properties.isSeedCategories()) {
            log.info("creation.kr category seed skipped (seed-categories=false)");
            return;
        }
        if (properties.getBoardMappings() == null || properties.getBoardMappings().isEmpty()) {
            return;
        }

        int created = 0;
        int updated = 0;

        for (Map.Entry<String, String> entry : properties.getBoardMappings().entrySet()) {
            String sitePath = entry.getKey();
            String boardId = entry.getValue();
            if (sitePath == null || sitePath.isBlank() || boardId == null || boardId.isBlank()) {
                continue;
            }

            String trimmedPath = sitePath.trim();
            String trimmedBoardId = boardId.trim();
            String label = properties.resolveBoardLabel(trimmedPath);

            Optional<Category> existing = categoryRepository.findByCode(trimmedPath);
            if (existing.isEmpty()) {
                existing = categoryRepository.findByName(label);
            }

            if (existing.isPresent()) {
                Category category = existing.get();
                boolean changed = false;
                if (category.getCode() == null || category.getCode().isBlank()) {
                    category.setCode(trimmedPath);
                    changed = true;
                }
                if (!trimmedPath.equals(category.getCreationKrSitePath())) {
                    category.setCreationKrSitePath(trimmedPath);
                    changed = true;
                }
                if (!trimmedBoardId.equals(category.getCreationKrBoardId())) {
                    category.setCreationKrBoardId(trimmedBoardId);
                    changed = true;
                }
                if (changed) {
                    categoryRepository.save(category);
                    updated++;
                }
            } else {
                categoryRepository.save(Category.builder()
                        .code(trimmedPath)
                        .name(label)
                        .creationKrSitePath(trimmedPath)
                        .creationKrBoardId(trimmedBoardId)
                        .build());
                created++;
            }
        }

        if (created > 0 || updated > 0) {
            log.info("creation.kr category seed complete: created={}, updated={}", created, updated);
        }
    }
}
