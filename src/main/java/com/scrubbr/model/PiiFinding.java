package com.scrubbr.model;

/** A masked piece of PII found in a file's content, with its occurrence count and page. */
public record PiiFinding(String type, String value, int count, int page) {
}
