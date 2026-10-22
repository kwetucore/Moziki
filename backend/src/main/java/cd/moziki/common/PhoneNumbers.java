package cd.moziki.common;

import java.util.regex.Pattern;

/** Normalises DRC mobile numbers to E.164 so web, USSD and mobile agree on identity. */
public final class PhoneNumbers {

    private static final Pattern E164 = Pattern.compile("^\\+[1-9][0-9]{7,14}$");
    private static final Pattern DRC_LOCAL = Pattern.compile("^0[0-9]{8,9}$");

    private PhoneNumbers() {
    }

    public static String normalize(String raw) {
        if (raw == null) {
            throw ApiException.badRequest("PHONE_INVALID", "error.auth.phone_invalid");
        }
        String value = raw.replaceAll("[\\s().-]", "");
        if (value.startsWith("00")) {
            value = "+" + value.substring(2);
        } else if (value.startsWith("243")) {
            value = "+" + value;
        } else if (DRC_LOCAL.matcher(value).matches()) {
            value = "+243" + value.substring(1);
        }
        if (!E164.matcher(value).matches()) {
            throw ApiException.badRequest("PHONE_INVALID", "error.auth.phone_invalid");
        }
        return value;
    }

    public static String masked(String phone) {
        if (phone == null || phone.length() < 6) {
            return "***";
        }
        return phone.substring(0, 4) + "****" + phone.substring(phone.length() - 2);
    }
}
