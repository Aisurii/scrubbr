package com.scrubbr.service;

import com.scrubbr.model.PiiFinding;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PiiDetectorTest {

    private final PiiDetector detector = new PiiDetector();

    @Test
    void findsEmailAddresses() {
        var matches = detector.findMatches("Reach me at jane.doe@example.com any time.");
        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).type()).isEqualTo(PiiDetector.PiiType.EMAIL);
        assertThat(matches.get(0).text()).isEqualTo("jane.doe@example.com");
    }

    @Test
    void findsValidCreditCardButNotRandomDigits() {
        // 4111 1111 1111 1111 is a well-known Luhn-valid test number.
        var valid = detector.findMatches("Card: 4111 1111 1111 1111");
        assertThat(valid).anyMatch(m -> m.type() == PiiDetector.PiiType.CREDIT_CARD);

        // A 16-digit string that fails the Luhn check should not be flagged as a card.
        var invalid = detector.findMatches("Ref 1234 5678 9012 3456 9999");
        assertThat(invalid).noneMatch(m -> m.type() == PiiDetector.PiiType.CREDIT_CARD);
    }

    @Test
    void findsPhoneAndSsn() {
        var matches = detector.findMatches("Call +1 (415) 555-0132 — SSN 123-45-6789.");
        assertThat(matches).anyMatch(m -> m.type() == PiiDetector.PiiType.PHONE);
        assertThat(matches).anyMatch(m -> m.type() == PiiDetector.PiiType.SSN);
    }

    @Test
    void longerMatchWinsWhenPatternsOverlap() {
        // The card number must not also be reported as a phone number.
        var matches = detector.findMatches("4111111111111111");
        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).type()).isEqualTo(PiiDetector.PiiType.CREDIT_CARD);
    }

    @Test
    void summariseCountsAndMasks() {
        String text = "a@b.com wrote to a@b.com";
        List<PiiFinding> findings = detector.summarise(detector.findMatches(text), 1);
        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).count()).isEqualTo(2);
        assertThat(findings.get(0).value()).contains("@b.com");
        assertThat(findings.get(0).value()).doesNotContain("a@b.com");
    }

    @Test
    void cleanTextProducesNoFindings() {
        assertThat(detector.findMatches("The quick brown fox jumps over the lazy dog.")).isEmpty();
    }
}
