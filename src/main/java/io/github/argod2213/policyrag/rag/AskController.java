package io.github.argod2213.policyrag.rag;

import io.github.argod2213.policyrag.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class AskController {

    private final AnswerService answers;
    private final CurrentUser currentUser;

    public AskController(AnswerService answers, CurrentUser currentUser) {
        this.answers = answers;
        this.currentUser = currentUser;
    }

    @PostMapping("/ask")
    public AnswerResponse ask(@Valid @RequestBody AskRequest request, Authentication auth) {
        return answers.answer(request.question(), request.topK(), currentUser.visibleAudiences(auth));
    }

    public record AskRequest(@NotBlank @Size(max = 2000) String question,
                             @Min(1) @Max(20) Integer topK) {
    }
}
