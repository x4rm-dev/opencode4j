package io.opencode4j.examples;

import io.opencode4j.agent.runtime.OpenCodeAgentRuntime;
import io.opencode4j.runtime.AgentResult;
import io.opencode4j.runtime.AgentSession;
import io.opencode4j.runtime.event.StepEndedEvent;
import io.opencode4j.runtime.event.ToolCallCompletedEvent;
import io.opencode4j.runtime.media.MediaResult;
import io.opencode4j.runtime.spec.AgentSpec;
import io.opencode4j.runtime.spec.PermissionRule;
import io.opencode4j.runtime.tool.Param;
import io.opencode4j.runtime.tool.Tool;
import io.opencode4j.runtime.tool.ToolContext;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Spike S2/S3: do screenshots returned by a JVM tool reach the model, and how does the context grow
 * over many sequential screenshot calls? Each screenshot renders a unique code; at the end the agent
 * is asked for the code of an early screenshot to see whether old images are retained.
 *
 * <p>Env: OPENCODE_MODEL, SPIKE_SHOTS (default 12).
 */
public final class ScreenshotLoopSpike {

    public static class ScreenTools {
        private final AtomicInteger seq = new AtomicInteger();
        private final List<String> codes = new ArrayList<>();

        @Tool(description = "Captures the current screen and returns it as an image. The screen shows a code.")
        public MediaResult screenshot(
                @Param(name = "reason", description = "why the screenshot is taken") String reason,
                ToolContext ctx) throws IOException {
            int n = seq.incrementAndGet();
            String code = "CODE-" + (1000 + (int) (Math.random() * 8999));
            codes.add(code);
            System.out.printf("  [tool] screenshot #%d -> %s%n", n, code);
            BufferedImage img = new BufferedImage(1280, 800, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, 1280, 800);
            g.setColor(Color.BLACK);
            g.setFont(new Font("SansSerif", Font.BOLD, 96));
            g.drawString("#" + n + "  " + code, 100, 400);
            g.dispose();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO_write(img, out);
            return MediaResult.image(out.toByteArray(), "image/png", "shot-" + n + ".png", null);
        }

        List<String> codes() {
            return codes;
        }
    }

    private static void ImageIO_write(BufferedImage img, ByteArrayOutputStream out) throws IOException {
        javax.imageio.ImageIO.write(img, "png", out);
    }

    public static void main(String[] args) throws Exception {
        int shots = Integer.parseInt(System.getenv().getOrDefault("SPIKE_SHOTS", "12"));
        try (ExampleHelper env = ExampleHelper.init("Spike S2/S3: screenshot loop")) {
            ScreenTools tools = new ScreenTools();
            AgentSpec spec = AgentSpec.builder("shooter")
                    .description("Takes screenshots on request")
                    .instruction("You operate a computer. Use the screenshot tool as instructed, one call at a time, "
                            + "and read the code visible in each screenshot. Never guess codes.")
                    .model(env.model())
                    .javaTools(tools)
                    .permission(PermissionRule.allow("*", "*"))
                    .build();

            OpenCodeAgentRuntime runtime = OpenCodeAgentRuntime.of(env.client());
            try (AgentSession session = runtime.open(spec)) {
                List<Long> stepInputs = new ArrayList<>();
                var run = session.send(
                        "Call the screenshot tool exactly " + shots + " times, sequentially, one per step, waiting for each "
                                + "result before the next call. After the last one, reply with a comma separated list of "
                                + "ALL codes you saw in order, then DONE.",
                        event -> {
                            if (event instanceof StepEndedEvent s) {
                                stepInputs.add(s.tokens().input() + s.tokens().cacheRead());
                                System.out.printf("  [step] input=%d cacheRead=%d output=%d%n",
                                        s.tokens().input(), s.tokens().cacheRead(), s.tokens().output());
                            } else if (event instanceof ToolCallCompletedEvent tc) {
                                System.out.printf("  [toolcall done] %s output=%s%n", tc.toolName(),
                                        String.valueOf(tc.output()).substring(0,
                                                Math.min(80, String.valueOf(tc.output()).length())));
                            }
                        });
                AgentResult result = run.await();
                System.out.println("\nSuccess: " + result.isSuccess() + " truncated=" + result.truncated());
                System.out.println("Final text: " + result.text());
                System.out.println("Actual codes: " + tools.codes());
                System.out.println("Per-step prompt tokens (input+cacheRead): " + stepInputs);
                System.out.println("Total tokens: " + result.tokenUsage() + " cost=" + result.totalCost());

                // Retention probe: ask about shot #1 without calling the tool.
                var probe = session.send(
                        "WITHOUT calling any tool, what code was shown in screenshot #1? If you cannot see it anymore, say NOT VISIBLE.");
                System.out.println("Probe (#1 expected " + tools.codes().get(0) + "): " + probe.await().text());
            }
            runtime.close();
        }
    }
}
