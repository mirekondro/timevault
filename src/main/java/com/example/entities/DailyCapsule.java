package com.example.entities;

import com.example.support.DateFormats;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class DailyCapsule {

    private Long id;
    private LocalDate capsuleDate;
    private String headline;
    private String vibeSummary;
    private String trendingTopics;
    private LocalDateTime createdAt;

    public DailyCapsule() {
    }

    public DailyCapsule(
            Long id,
            LocalDate capsuleDate,
            String headline,
            String vibeSummary,
            String trendingTopics,
            LocalDateTime createdAt
    ) {
        this.id = id;
        this.capsuleDate = capsuleDate;
        this.headline = headline;
        this.vibeSummary = vibeSummary;
        this.trendingTopics = trendingTopics;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDate getCapsuleDate() {
        return capsuleDate;
    }

    public void setCapsuleDate(LocalDate capsuleDate) {
        this.capsuleDate = capsuleDate;
    }

    public String getHeadline() {
        return headline == null ? "Denmark Today" : headline;
    }

    public void setHeadline(String headline) {
        this.headline = headline;
    }

    public String getVibeSummary() {
        return vibeSummary == null ? "" : vibeSummary;
    }

    public void setVibeSummary(String vibeSummary) {
        this.vibeSummary = vibeSummary;
    }

    public String getTrendingTopics() {
        return trendingTopics == null ? "" : trendingTopics;
    }

    public void setTrendingTopics(String trendingTopics) {
        this.trendingTopics = trendingTopics;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String createdAtLabel() {
        if (createdAt == null) {
            return capsuleDate == null ? "Unknown day" : capsuleDate.format(DateFormats.DISPLAY_DATE);
        }
        return createdAt.format(DateFormats.DISPLAY_DATE_TIME);
    }
}
