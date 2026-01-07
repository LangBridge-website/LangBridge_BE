package com.project.Transflow.translate.controller;


import com.project.Transflow.translate.dto.TranslationRequest;
import com.project.Transflow.translate.dto.TranslationResponse;
import com.project.Transflow.translate.service.TransflowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/translate")
@RequiredArgsConstructor
public class TranslationController {

    private final TransflowService transflowService;

    @PostMapping("/webpage")
    public ResponseEntity<TranslationResponse> translateWebPage(@RequestBody TranslationRequest request) {
        log.info("번역 요청 받음 - URL: {}, Target: {}", request.getUrl(), request.getTargetLang());

        TranslationResponse response = transflowService.translateWebPage(request);

        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Translation service is running!");
    }
}