package com.crackhash.requests;

import jakarta.xml.bind.annotation.*;
        import lombok.Data;

import java.util.List;

@XmlRootElement(name = "CrackHashWorkerRequest", namespace = "http://ccfit.nsu.ru/schema/crack-hash-request")
@XmlAccessorType(XmlAccessType.FIELD)
@Data
public class WorkerRequest {

    @XmlElement(name = "RequestId")
    private String requestId;

    @XmlElement(name = "PartNumber")
    private int partNumber;

    @XmlElement(name = "PartCount")
    private int partCount;

    @XmlElement(name = "Hash")
    private String hash;

    @XmlElement(name = "MaxLength")
    private int maxLength;

    @XmlElementWrapper(name = "Alphabet")
    @XmlElement(name = "symbols")
    private List<String> alphabet;
}