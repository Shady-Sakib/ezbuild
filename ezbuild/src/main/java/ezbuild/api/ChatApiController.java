package ezbuild.api;

import ezbuild.assistant.AssistantService;
import ezbuild.controller.CurrentUser;
import ezbuild.dto.ChatReply;
import ezbuild.dto.ChatRequest;
import ezbuild.model.ChatMessage;
import ezbuild.model.ChatSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatApiController {

    private final AssistantService assistantService;
    private final CurrentUser currentUser;

    @PostMapping
    public ChatReply chat(@Valid @RequestBody ChatRequest body, Authentication auth) {
        return assistantService.chat(currentUser.require(auth), body.sessionId(), body.message());
    }

    @GetMapping("/sessions")
    public List<ChatSession> sessions(Authentication auth) {
        return assistantService.sessions(currentUser.require(auth));
    }

    @GetMapping("/sessions/{id}")
    public List<ChatMessage> messages(@PathVariable String id, Authentication auth) {
        return assistantService.messages(currentUser.require(auth), id);
    }

    @DeleteMapping("/sessions/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id, Authentication auth) {
        assistantService.delete(currentUser.require(auth), id);
        return ResponseEntity.noContent().build();
    }
}
