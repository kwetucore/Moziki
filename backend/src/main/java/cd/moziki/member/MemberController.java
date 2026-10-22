package cd.moziki.member;

import cd.moziki.member.dto.CreateMemberRequest;
import cd.moziki.member.dto.MemberResponse;
import cd.moziki.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/members")
public class MemberController {

    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('LEADER','AUDITOR')")
    public List<MemberResponse> list(@AuthenticationPrincipal UserPrincipal principal) {
        return memberService.list(principal);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('LEADER')")
    public MemberResponse create(@Valid @RequestBody CreateMemberRequest request,
                                 @AuthenticationPrincipal UserPrincipal principal) {
        return memberService.create(request, principal);
    }
}
