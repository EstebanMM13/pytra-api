package com.estebanmm13.pytra_api.error;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@NoArgsConstructor
@AllArgsConstructor
@Getter
public class ApiError {

    private int status;
    private String message;
    private Map<String, String> fieldErrors;
    private LocalDateTime timestamp;

}
