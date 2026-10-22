package cd.moziki.auth.dto;

import cd.moziki.cooperative.dto.CooperativeSummary;
import cd.moziki.user.dto.UserProfile;

public record MeResponse(UserProfile user, CooperativeSummary cooperative) {
}
