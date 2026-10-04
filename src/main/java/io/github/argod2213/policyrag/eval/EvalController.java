package io.github.argod2213.policyrag.eval;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequestMapping("/api/eval")
public class EvalController {

    private final EvalService evalService;

    public EvalController(EvalService evalService) {
        this.evalService = evalService;
    }

    /** Runs the bundled evaluation set against the live models and vector store. */
    @PostMapping("/run")
    @PreAuthorize("hasRole('ADMIN')")
    public EvalReport run() throws IOException {
        return evalService.run();
    }
}
