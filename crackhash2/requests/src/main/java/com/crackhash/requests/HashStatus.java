package com.crackhash.requests;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class HashStatus {
    private Status status;
    private List<String> data;
    private int progress;
}
