package com.fintrack.parser;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

public interface StatementParser {

    boolean supports(MultipartFile file);

    List<ParsedStatementRow> parse(MultipartFile file) throws IOException;

    String formatName();
}
