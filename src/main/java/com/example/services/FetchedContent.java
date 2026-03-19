package com.example.services;

public record FetchedContent(
        String requestUrl,
        String finalUrl,
        String title,
        String content,
        String rawContent,
        String sourcePlatform
) {
}
