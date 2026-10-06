package io.opencode4j.examples;

import io.opencode4j.agent.runtime.OpenCodeAgentRuntime;
import io.opencode4j.runtime.AgentResult;
import io.opencode4j.runtime.AgentRun;
import io.opencode4j.runtime.AgentSession;
import io.opencode4j.runtime.RunPrompt;
import io.opencode4j.runtime.event.TextDeltaEvent;
import io.opencode4j.runtime.event.ToolCallCompletedEvent;
import io.opencode4j.runtime.media.MediaAttachment;
import io.opencode4j.runtime.media.MediaResult;
import io.opencode4j.runtime.spec.AgentSpec;
import io.opencode4j.runtime.spec.PermissionRule;
import io.opencode4j.runtime.tool.Param;
import io.opencode4j.runtime.tool.Tool;
import io.opencode4j.runtime.tool.ToolContext;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;

/**
 * Example 7: Multimodal Prompts and Media Tool Outputs.
 *
 * <p>Demonstrates:
 * <ul>
 *     <li>Attaching an in-memory image to a prompt turn using {@link MediaAttachment} (RFC 2397 Data URI)</li>
 *     <li>A native Java tool that dynamically renders a chart and returns {@link MediaResult}</li>
 *     <li>Streaming multimodal agent analysis in real time</li>
 * </ul>
 */
public final class MultimodalPromptExample {

    /** Native Java service producing multimodal media outputs for the agent. */
    public static class ChartGeneratorTool {

        @Tool(description = "Generates a system latency graph PNG image for a named microservice")
        public MediaResult generateLatencyChart(
                @Param(name = "serviceName", description = "Target service, e.g. payment-service") String serviceName,
                ToolContext ctx
        ) throws IOException {
            System.out.printf("  [JVM Tool Invoked] generateLatencyChart(%s) in session %s%n", serviceName, ctx.sessionId());

            // Dynamically render a simple 200x80 chart PNG in memory
            BufferedImage img = new BufferedImage(200, 80, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            g.setColor(new Color(24, 24, 27));
            g.fillRect(0, 0, 200, 80);
            g.setColor(new Color(16, 185, 129));
            g.drawString(serviceName + " P99: 45ms", 20, 45);
            g.dispose();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "png", baos);
            byte[] pngBytes = baos.toByteArray();

            return MediaResult.image(pngBytes, "image/png", serviceName + "-chart.png", "P99 latency is 45ms");
        }
    }

    public static void main(String[] args) throws Exception {
        try (ExampleHelper env = ExampleHelper.init("Example 7: Multimodal Prompts & Media Tools")) {
            AgentSpec spec = AgentSpec.builder("multimodal_analyst")
                    .description("Multimodal systems analyst capable of analyzing images and producing charts")
                    .instruction("You are an infrastructure analyst. When asked about system metrics, generate a latency "
                            + "chart using the generateLatencyChart tool and summarize the system state.")
                    .model(env.model())
                    .javaTools(new ChartGeneratorTool())
                    .permission(PermissionRule.allow("*", "*"))
                    .build();

            OpenCodeAgentRuntime runtime = OpenCodeAgentRuntime.of(env.client());

            try (AgentSession session = runtime.open(spec)) {
                // 1. Create an in-memory test diagram PNG (120x60 red banner with text)
                byte[] sampleImageBytes = createSampleImage("System Alert: High Load");
                MediaAttachment attachedImage = MediaAttachment.image(
                        sampleImageBytes,
                        "image/png",
                        "alert_banner.png",
                        "Incident banner showing high system load"
                );

                // 2. Build multimodal prompt
                String promptText = "Analyze this attached alert image, then generate a latency chart for 'auth-service'.";
                RunPrompt prompt = RunPrompt.builder(promptText)
                        .attach(attachedImage)
                        .build();

                System.out.println("User: " + promptText);
                System.out.println("Attached: " + attachedImage.name() + " (" + attachedImage.mimeType() + ", "
                        + sampleImageBytes.length + " bytes)\n");
                System.out.print("Agent: ");

                AgentRun run = session.send(prompt, event -> {
                    if (event instanceof TextDeltaEvent delta) {
                        System.out.print(delta.delta());
                        System.out.flush();
                    } else if (event instanceof ToolCallCompletedEvent tc) {
                        System.out.printf("%n  [Tool Completed: %s output=%s]%n  ", tc.toolName(), tc.output());
                    }
                });

                AgentResult result = run.await();

                System.out.println("\n\n--- Result Summary ---");
                System.out.println("Success:    " + result.isSuccess());
                System.out.println("Total Cost: $" + result.totalCost());
                System.out.println("Tokens:     " + result.tokenUsage().total());
                System.out.println("Tools Used: " + result.toolExecutions().size());
            }

            runtime.close();
        }
    }

    private static byte[] createSampleImage(String label) throws IOException {
        BufferedImage img = new BufferedImage(160, 50, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(220, 38, 38)); // Red banner
        g.fillRect(0, 0, 160, 50);
        g.setColor(Color.WHITE);
        g.drawString(label, 10, 30);
        g.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", baos);
        return baos.toByteArray();
    }
}
