package ezbuild.dto;

import java.util.List;

public record ChatReply(String sessionId, String text, List<BuildPlan> builds, List<String> suggestions,
                        boolean degraded) { }
