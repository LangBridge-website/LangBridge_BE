package com.project.Transflow.category.service;

import com.project.Transflow.category.dto.CategoryResponse;
import com.project.Transflow.category.dto.CreateCategoryRequest;
import com.project.Transflow.category.dto.UpdateCategoryRequest;
import com.project.Transflow.category.entity.Category;
import com.project.Transflow.category.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;

    @Transactional
    public CategoryResponse createCategory(CreateCategoryRequest request) {
        // 중복 체크
        if (categoryRepository.existsByName(request.getName())) {
            throw new IllegalArgumentException("이미 존재하는 카테고리 이름입니다: " + request.getName());
        }

        Category category = Category.builder()
                .name(request.getName())
                .description(request.getDescription())
                .build();

        Category saved = categoryRepository.save(category);
        log.info("카테고리 생성: {} (id: {})", saved.getName(), saved.getId());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> findAll() {
        return categoryRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Optional<CategoryResponse> findById(Long id) {
        return categoryRepository.findById(id)
                .map(this::toResponse);
    }

    @Transactional
    public CategoryResponse updateCategory(Long id, UpdateCategoryRequest request) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("카테고리를 찾을 수 없습니다: " + id));

        if (request.getName() != null) {
            // 이름 변경 시 중복 체크 (현재 이름과 다를 때만)
            if (!category.getName().equals(request.getName()) && categoryRepository.existsByName(request.getName())) {
                throw new IllegalArgumentException("이미 존재하는 카테고리 이름입니다: " + request.getName());
            }
            category.setName(request.getName());
        }

        if (request.getDescription() != null) {
            category.setDescription(request.getDescription());
        }

        Category saved = categoryRepository.save(category);
        log.info("카테고리 수정: {} (id: {})", saved.getName(), saved.getId());
        return toResponse(saved);
    }

    @Transactional
    public void deleteCategory(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("카테고리를 찾을 수 없습니다: " + id));

        categoryRepository.delete(category);
        log.info("카테고리 삭제: {} (id: {})", category.getName(), id);
    }

    private CategoryResponse toResponse(Category category) {
        return CategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .description(category.getDescription())
                .createdAt(category.getCreatedAt())
                .updatedAt(category.getUpdatedAt())
                .build();
    }
}

