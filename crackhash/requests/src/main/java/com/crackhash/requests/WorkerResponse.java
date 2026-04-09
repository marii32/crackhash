package com.crackhash.requests;

import jakarta.xml.bind.annotation.*;
import lombok.Data;

import java.util.List;

@Data
@XmlRootElement(name = "CrackHashWorkerResponse",
        namespace = "http://ccfit.nsu.ru/schema/crack-hash-response")
@XmlAccessorType(XmlAccessType.FIELD)
public class WorkerResponse {

    @XmlElement(name = "RequestId")
    private String requestId;

    @XmlElement(name = "PartNumber")
    private int partNumber;

    @XmlElement(name = "Answers")
    private Answers answers;

    @Data
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Answers {
        @XmlElement(name = "words")
        private List<String> words;
    }
}
