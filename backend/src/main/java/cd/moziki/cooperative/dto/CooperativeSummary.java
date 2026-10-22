package cd.moziki.cooperative.dto;

import cd.moziki.cooperative.Cooperative;

import java.util.UUID;

public record CooperativeSummary(
        UUID id,
        String code,
        String name,
        String country,
        String currency,
        String defaultLocale
) {
    public static CooperativeSummary from(Cooperative cooperative) {
        return new CooperativeSummary(cooperative.getId(), cooperative.getCode(),
                cooperative.getName(), cooperative.getCountry(), cooperative.getCurrency(),
                cooperative.getDefaultLocale());
    }
}
