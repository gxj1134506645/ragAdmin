package com.ragadmin.server.document.parser;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SemanticChunkStrategyTest {

    private SemanticChunkStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new SemanticChunkStrategy();
    }

    @Test
    void shouldFindBreakpointsAtLowSimilarity() {
        List<Double> similarities = List.of(0.8, 0.7, 0.3, 0.9, 0.2, 0.85);

        List<Integer> breakpoints = strategy.findBreakpoints(similarities, 0.5);

        // 0.3 < 0.5 at index 2 → breakpoint at 3
        // 0.2 < 0.5 at index 4 → breakpoint at 5
        assertEquals(List.of(3, 5), breakpoints);
    }

    @Test
    void shouldReturnNoBreakpointsWhenAllSimilar() {
        List<Double> similarities = List.of(0.8, 0.9, 0.85, 0.88);

        List<Integer> breakpoints = strategy.findBreakpoints(similarities, 0.5);

        assertTrue(breakpoints.isEmpty());
    }

    @Test
    void shouldReturnAllBreakpointsWhenAllDissimilar() {
        List<Double> similarities = List.of(0.1, 0.2, 0.15);

        List<Integer> breakpoints = strategy.findBreakpoints(similarities, 0.5);

        assertEquals(List.of(1, 2, 3), breakpoints);
    }

    @Test
    void shouldBuildParentChildDraftsCorrectly() {
        List<String> childTexts = List.of("段落1", "段落2", "段落3", "段落4", "段落5");
        // Breakpoint after index 2 → splits into [段落1,段落2,段落3] and [段落4,段落5]
        List<Integer> breakpoints = List.of(3);

        List<ChunkDraft> drafts = strategy.buildParentChildDrafts(childTexts, breakpoints, 2400);

        assertFalse(drafts.isEmpty());
        // Should have parent drafts + child drafts
        // Group 1: [段落1, 段落2, 段落3], Group 2: [段落4, 段落5]
        // Each group: 1 parent + N children
        long parentCount = drafts.stream().filter(d -> d.metadata().get("isParent") != null).count();
        long childCount = drafts.stream().filter(d -> d.metadata().get("isChild") != null).count();
        assertEquals(2, parentCount);
        assertEquals(5, childCount);
    }

    @Test
    void shouldReturnPlainDraftsWhenSingleGroup() {
        List<String> childTexts = List.of("段落1", "段落2");
        // No breakpoints → single group → no parent-child
        List<Integer> breakpoints = List.of();

        List<ChunkDraft> drafts = strategy.buildParentChildDrafts(childTexts, breakpoints, 2400);

        assertEquals(2, drafts.size());
        assertNull(drafts.getFirst().parentChunkId());
    }

    @Test
    void shouldCalculateCosineSimilarityCorrectly() {
        List<Float> a = List.of(1.0f, 0.0f, 0.0f);
        List<Float> b = List.of(0.0f, 1.0f, 0.0f);
        List<Float> c = List.of(1.0f, 0.0f, 0.0f);

        assertEquals(0.0, strategy.cosineSimilarity(a, b), 0.001);
        assertEquals(1.0, strategy.cosineSimilarity(a, c), 0.001);
    }

    @Test
    void shouldHandleEmptySimilarities() {
        List<Integer> breakpoints = strategy.findBreakpoints(List.of(), 0.5);
        assertTrue(breakpoints.isEmpty());
    }

    @Test
    void shouldRespectParentMaxChars() {
        List<String> childTexts = List.of("A".repeat(200), "B".repeat(200), "C".repeat(200), "D".repeat(200));
        // No breakpoints, but parentMaxChars=300 should force splits
        List<Integer> breakpoints = List.of();

        List<ChunkDraft> drafts = strategy.buildParentChildDrafts(childTexts, breakpoints, 300);

        // Should split into multiple groups due to max chars
        long parentCount = drafts.stream().filter(d -> d.metadata().get("isParent") != null).count();
        assertTrue(parentCount > 1);
    }
}
