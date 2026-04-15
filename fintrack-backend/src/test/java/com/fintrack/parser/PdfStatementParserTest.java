package com.fintrack.parser;

import com.fintrack.entity.Transaction.TransactionType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PdfStatementParserTest {

    private final PdfStatementParser parser = new PdfStatementParser();

    @Test
    void parseTextSplitsMergedGooglePayTransactionsAndIgnoresTransactionCount() {
        String rawText = """
                Google Pay Statement
                March 2026
                28 MAR, 2026 06:35 PM Paid 349.00 To Gaming 29 MAR, 2026 08:00 AM Paid 1500.00 To Electronics 30 MAR, 2026 09:20 PM Paid 220.00 To Snacks Shop Transaction Count: 30
                """;

        List<ParsedStatementRow> rows = parser.parseText(rawText);

        assertThat(rows).hasSize(3);
        assertThat(rows).extracting(ParsedStatementRow::description)
                .containsExactly("Gaming", "Electronics", "Snacks Shop");
        assertThat(rows).extracting(ParsedStatementRow::transactionTime)
                .extracting(time -> time != null ? time.toString() : null)
                .containsExactly("18:35", "08:00", "21:20");
        assertThat(rows).allMatch(row -> !row.description().contains("Transaction Count"));
    }

    @Test
    void parseTextExtractsProvidedStatementIntoThirtyRecords() {
        String rawText = """
                Google Pay Statement
                March 2026
                01 MAR, 2026 06:15 AM Sent 550.00 To Arjun Mehta
                02 MAR, 2026 09:10 AM Paid 420.75 To Food Delivery
                03 MAR, 2026 01:40 PM Paid 180.00 To Cab Ride
                04 MAR, 2026 10:00 AM Received 52000.00 From Salary
                05 MAR, 2026 11:25 AM Paid 1350.00 To Online Shopping
                06 MAR, 2026 02:50 PM Paid 920.00 To Electricity Bill
                07 MAR, 2026 08:45 AM Paid 699.00 To OTT Subscription
                08 MAR, 2026 04:10 PM Sent 650.00 To Neha Verma
                09 MAR, 2026 01:00 PM Paid 480.00 To Food Order
                10 MAR, 2026 07:55 AM Paid 2500.00 To Fitness Center
                11 MAR, 2026 12:20 PM Paid 300.00 To Grocery Store
                12 MAR, 2026 03:45 PM Received 10000.00 From Freelance
                13 MAR, 2026 09:30 AM Paid 200.00 To Coffee Shop
                14 MAR, 2026 11:10 AM Paid 499.00 To Mobile Recharge
                15 MAR, 2026 02:15 PM Paid 750.00 To Movie Tickets
                16 MAR, 2026 06:40 PM Sent 9000.00 To Rent
                17 MAR, 2026 10:05 AM Paid 670.00 To Pharmacy
                18 MAR, 2026 01:25 PM Paid 1100.00 To Restaurant
                19 MAR, 2026 05:50 PM Received 3000.00 From Bonus
                20 MAR, 2026 08:15 AM Paid 2800.00 To Insurance
                21 MAR, 2026 10:45 AM Paid 950.00 To Internet Bill
                22 MAR, 2026 02:20 PM Sent 1200.00 To Gift
                23 MAR, 2026 04:10 PM Paid 160.00 To Taxi
                24 MAR, 2026 07:00 PM Paid 4200.00 To Car Service
                25 MAR, 2026 09:30 AM Received 500.00 From Refund
                26 MAR, 2026 11:50 AM Paid 850.00 To Salon
                27 MAR, 2026 03:10 PM Sent 600.00 To Charity
                28 MAR, 2026 06:35 PM Paid 349.00 To Gaming
                29 MAR, 2026 08:00 AM Paid 1500.00 To Electronics
                30 MAR, 2026 09:20 PM Paid 220.00 To Snacks Shop
                Transaction Count: 30
                """;

        List<ParsedStatementRow> rows = parser.parseText(rawText);

        assertThat(rows).hasSize(30);
        assertThat(rows).filteredOn(row -> row.type() == TransactionType.INCOME).hasSize(4);
        assertThat(rows).filteredOn(row -> row.type() == TransactionType.EXPENSE).hasSize(26);
        assertThat(rows.get(0).description()).isEqualTo("Arjun Mehta");
        assertThat(rows.get(3).description()).isEqualTo("Salary");
        assertThat(rows.get(29).description()).isEqualTo("Snacks Shop");
    }
}
