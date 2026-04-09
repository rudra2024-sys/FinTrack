package com.fintrack.parser;

import com.fintrack.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Component
public class StatementParserRegistry {

    private final List<StatementParser> parsers;

    public StatementParserRegistry(List<StatementParser> parsers) {
        this.parsers = parsers;
    }

    public StatementParser resolve(MultipartFile file) {
        return parsers.stream()
                .filter(parser -> parser.supports(file))
                .findFirst()
                .orElseThrow(() -> new ApiException(
                        "Unsupported statement format. Upload a CSV now or a machine-readable PDF.",
                        HttpStatus.BAD_REQUEST
                ));
    }
}
