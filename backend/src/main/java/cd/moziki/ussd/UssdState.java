package cd.moziki.ussd;

public enum UssdState {
    IDLE,
    AUTHENTICATING,
    MENU,
    CONFIRMING,
    COMPLETED,
    EXPIRED
}
