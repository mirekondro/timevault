package com.example.desktop.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Structured dialog outcome containing inline validation, form messages, and toast targets.
 */
public record DialogActionResult(
        boolean success,
        Map<String, String> fieldErrors,
        String formMessage,
        ToastNotificationType localToastType,
        String localToastMessage,
        ToastNotificationType mainToastType,
        String mainToastMessage) {

    public DialogActionResult {
        fieldErrors = fieldErrors == null ? Map.of() : Map.copyOf(fieldErrors);
    }

    public static DialogActionResult successful() {
        return new DialogActionResult(true, Map.of(), null, null, null, null, null);
    }

    public static DialogActionResult successMainToast(String message) {
        return successful().withMainToast(ToastNotificationType.SUCCESS, message);
    }

    public static DialogActionResult successLocalToast(String message) {
        return successful().withLocalToast(ToastNotificationType.SUCCESS, message);
    }

    public static DialogActionResult failure() {
        return new DialogActionResult(false, Map.of(), null, null, null, null, null);
    }

    public static DialogActionResult fieldError(String fieldId, String message) {
        return failure().withFieldError(fieldId, message);
    }

    public DialogActionResult withFieldError(String fieldId, String message) {
        if (fieldId == null || fieldId.isBlank() || message == null || message.isBlank()) {
            return this;
        }
        Map<String, String> nextFieldErrors = new LinkedHashMap<>(fieldErrors);
        nextFieldErrors.put(fieldId, message);
        return new DialogActionResult(success, nextFieldErrors, formMessage, localToastType, localToastMessage, mainToastType, mainToastMessage);
    }

    public DialogActionResult withFormMessage(String message) {
        return new DialogActionResult(success, fieldErrors, blankToNull(message), localToastType, localToastMessage, mainToastType, mainToastMessage);
    }

    public DialogActionResult withLocalToast(ToastNotificationType type, String message) {
        return new DialogActionResult(success, fieldErrors, formMessage, type, blankToNull(message), mainToastType, mainToastMessage);
    }

    public DialogActionResult withMainToast(ToastNotificationType type, String message) {
        return new DialogActionResult(success, fieldErrors, formMessage, localToastType, localToastMessage, type, blankToNull(message));
    }

    public boolean hasFieldErrors() {
        return !fieldErrors.isEmpty();
    }

    public boolean hasFormMessage() {
        return formMessage != null && !formMessage.isBlank();
    }

    public boolean hasLocalToast() {
        return localToastType != null && localToastMessage != null && !localToastMessage.isBlank();
    }

    public boolean hasMainToast() {
        return mainToastType != null && mainToastMessage != null && !mainToastMessage.isBlank();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
