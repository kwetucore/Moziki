package cd.moziki.transparency;

import cd.moziki.security.UserPrincipal;
import cd.moziki.transparency.dto.TransparencySummary;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transparency")
public class TransparencyController {

    private final TransparencyService transparencyService;

    public TransparencyController(TransparencyService transparencyService) {
        this.transparencyService = transparencyService;
    }

    /** Any signed-in member of the cooperative can see accountability metrics. */
    @GetMapping("/summary")
    public TransparencySummary summary(@AuthenticationPrincipal UserPrincipal principal) {
        return transparencyService.summary(principal);
    }
}
