package cd.moziki.account.dto;

import cd.moziki.account.AccountCategory;
import cd.moziki.account.AccountType;
import cd.moziki.account.WalletProvider;

import java.util.UUID;

public record AccountResponse(
        UUID id,
        String code,
        String name,
        AccountType type,
        AccountCategory category,
        WalletProvider walletProvider,
        String currency,
        long balanceMinor,
        long displayBalanceMinor,
        UUID memberId,
        String memberName,
        boolean systemAccount
) {
}
