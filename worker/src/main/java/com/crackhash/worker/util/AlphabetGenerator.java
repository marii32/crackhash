package com.crackhash.worker.util;

import java.util.ArrayList;
import java.util.List;
import org.paukov.combinatorics.Generator;
import org.paukov.combinatorics.ICombinatoricsVector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static org.paukov.combinatorics.CombinatoricsFactory.createPermutationWithRepetitionGenerator;
import static org.paukov.combinatorics.CombinatoricsFactory.createVector;

public class AlphabetGenerator {
    private static final Logger logger = LoggerFactory.getLogger(AlphabetGenerator.class);
    private final List<String> alphabet;
    private final int maxLength;

    public AlphabetGenerator(List<String> alphabet, int maxLength) {
        this.alphabet = alphabet;
        this.maxLength = maxLength;
    }

    public List<String> getPart(int partNumber, int partCount) {
        logger.info("Generating part {} of {} for words with maxLength: {}", partNumber + 1, partCount, maxLength);
        ICombinatoricsVector<String> vector = createVector(alphabet);
        List<String> partWords = new ArrayList<>();
        long total = 0;
        for (int wordSize = 1; wordSize <= maxLength; wordSize++) {
            long count = 1;
            for (int i = 0; i < wordSize; i++) {
                count *= alphabet.size();
            }
            total += count;
        }
        long chunk = total / partCount;
        long start = partNumber * chunk;
        long end = (partNumber == partCount - 1) ? total : (partNumber + 1) * chunk;
        long globalIndex = 0;
        long processed = 0;
        long totalPart = end - start;
        for (int wordSize = maxLength; wordSize > 0; wordSize--) {
            Generator<String> generator = createPermutationWithRepetitionGenerator(vector, wordSize);
            int totalSize = (int) generator.getNumberOfGeneratedObjects();
            int chunkSize = totalSize / partCount;
            int startIndex = partNumber * chunkSize;
            int endIndex = (partNumber == partCount - 1) ? totalSize : (partNumber + 1) * chunkSize;
            int index = 0;
            for (ICombinatoricsVector<String> word : generator) {
                if (index >= startIndex && index < endIndex) {
                    partWords.add(convertToString(word));
                }
                index++;
                if (index >= endIndex) break;
            }
        }
        logger.info("Generated word list with {} words", partWords.size());
        return partWords;
    }

    private String convertToString(ICombinatoricsVector<String> vector) {
        StringBuilder sb = new StringBuilder();
        for (String element : vector) {
            sb.append(element);
        }
        return sb.toString();
    }
}