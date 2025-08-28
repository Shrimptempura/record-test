package com.mytest.oplib.util;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;

public final class OpenApiResultValidator {

    private static final Set<String> OK = Set.of(
            "200", "00", "0000", "K0", "NORMAL_SERVICE", "NORMAL_CODE", "SUCCESS"
    );

    private OpenApiResultValidator() {}

    public static void validate(String code, String msg) {
        String c = code == null ? "" : code.trim();
        String m = msg  == null ? "" : msg.trim();

        boolean ok = OK.contains(c) || OK.contains(m.toUpperCase());
        if (ok) return;

        String friendly = switch (c) {
            case "K20" -> "서비스 접근이 거부되었습니다.";
            case "K21" -> "서비스키가 일시적으로 사용할 수 없습니다.";
            case "K22" -> "요청 제한 횟수를 초과했습니다.";
            case "K30" -> "등록되지 않은 서비스키입니다.";
            case "K31" -> "서비스키의 유효기간이 만료되었습니다.";
            case "K32" -> "등록되지 않은 IP입니다.";
            case "K33" -> "서명되지 않은 호출입니다.";
            case "K03" -> "데이터가 없습니다.";
            default     -> null;
        };

        String reason = (friendly != null ? friendly + " " : "") + "(" + c + " - " + m + ")";

        HttpStatus status = switch (c) {
            case "K10","K11","K12","K20","K21","K22","K30","K31","K32","K33" -> HttpStatus.BAD_REQUEST;
            case "K03" -> HttpStatus.OK; // 데이터 없음은 서비스에서 빈 결과로 처리 권장
            case "K05","K90","K99","K02","K04","K01" -> HttpStatus.BAD_GATEWAY;
            default -> HttpStatus.BAD_GATEWAY;
        };

        if (status == HttpStatus.OK) {
            return; // K03은 여기서 예외 던지지 않음
        }
        throw new ResponseStatusException(status, "Seat API 실패: " + reason);
    }
}
