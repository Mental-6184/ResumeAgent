package com.resumeagent.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class EmbeddingService {

    private static final int DIMENSION = 128;

    public double[] embed(String text) {
        double[] vector = new double[DIMENSION];
        for (String token : tokenize(text)) {
            int bucket = Math.floorMod(token.hashCode(), DIMENSION);
            vector[bucket] += 1.0;
        }
        normalize(vector);
        return vector;
    }

    public double cosineSimilarity(double[] left, double[] right) {
        if (left.length != right.length) {
            return 0.0;
        }
        double dot = 0.0;
        double leftMagnitude = 0.0;
        double rightMagnitude = 0.0;
        for (int index = 0; index < left.length; index++) {
            dot += left[index] * right[index];
            leftMagnitude += left[index] * left[index];
            rightMagnitude += right[index] * right[index];
        }
        if (leftMagnitude == 0.0 || rightMagnitude == 0.0) {
            return 0.0;
        }
        return dot / (Math.sqrt(leftMagnitude) * Math.sqrt(rightMagnitude));
    }

    private List<String> tokenize(String text) {
        String normalized = text == null ? "" : text.toLowerCase(Locale.ROOT);
        List<String> tokens = new ArrayList<>();
        StringBuilder asciiToken = new StringBuilder();
        StringBuilder chineseToken = new StringBuilder();

        for (int index = 0; index < normalized.length(); index++) {
            char character = normalized.charAt(index);
            if (Character.isLetterOrDigit(character)) {
                if (isChineseCharacter(character)) {
                    flushAsciiToken(tokens, asciiToken);
                    chineseToken.append(character);
                } else {
                    flushChineseToken(tokens, chineseToken);
                    asciiToken.append(character);
                }
            } else {
                flushAsciiToken(tokens, asciiToken);
                flushChineseToken(tokens, chineseToken);
            }
        }

        flushAsciiToken(tokens, asciiToken);
        flushChineseToken(tokens, chineseToken);
        return tokens;
    }

    private boolean isChineseCharacter(char character) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(character);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B;
    }

    private void flushAsciiToken(List<String> tokens, StringBuilder builder) {
        if (builder.length() > 0) {
            tokens.add(builder.toString());
            builder.setLength(0);
        }
    }

    private void flushChineseToken(List<String> tokens, StringBuilder builder) {
        if (builder.length() == 0) {
            return;
        }
        String token = builder.toString();
        tokens.add(token);
        for (int index = 0; index < token.length(); index++) {
            tokens.add(String.valueOf(token.charAt(index)));
        }
        if (token.length() > 1) {
            for (int index = 0; index < token.length() - 1; index++) {
                tokens.add(token.substring(index, index + 2));
            }
        }
        builder.setLength(0);
    }

    private void normalize(double[] vector) {
        double magnitude = 0.0;
        for (double value : vector) {
            magnitude += value * value;
        }
        if (magnitude == 0.0) {
            return;
        }
        double sqrt = Math.sqrt(magnitude);
        for (int index = 0; index < vector.length; index++) {
            vector[index] = vector[index] / sqrt;
        }
    }
}

