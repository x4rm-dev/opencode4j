package io.opencode4j.runtime.media;

/**
 * Image resolution fidelity for multimodal models (e.g. OpenAI vision detail parameter).
 *
 * <ul>
 *     <li>{@link #LOW}: Faster and token-efficient. Evaluates image as a 512x512 low-res thumbnail (~85 tokens).</li>
 *     <li>{@link #HIGH}: Full-resolution multi-tile analysis for detailed inspection (diagrams, text, charts).</li>
 *     <li>{@link #AUTO}: Default. Lets the model decide based on input image dimensions.</li>
 * </ul>
 */
public enum ImageDetail {
    AUTO,
    LOW,
    HIGH;

    public String wireValue() {
        return name().toLowerCase();
    }
}
