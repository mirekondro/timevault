package com.example.desktop.model;

import java.util.Locale;

/**
 * One desktop language option shown in the account menu.
 */
public record LanguageOption(String code, Locale locale, String labelKey, boolean available) {
}
