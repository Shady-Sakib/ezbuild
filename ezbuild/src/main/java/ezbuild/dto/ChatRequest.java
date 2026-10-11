package ezbuild.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatRequest(String sessionId,
                          @NotBlank(message = "Type a message.")
                          @Size(max = 500, message = "Keep messages under 500 characters.") String message) { }
