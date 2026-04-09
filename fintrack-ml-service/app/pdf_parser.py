from __future__ import annotations

import re
from datetime import datetime
from io import BytesIO
from typing import List, Optional, Tuple

from pypdf import PdfReader

from .models import PdfExtractionResponse, TransactionRecord


# Enhanced regex patterns for better matching
DATE_RE = r"(?P<date>\d{1,2}[/-]\d{1,2}[/-]\d{2,4}|\d{1,2}\s+[A-Za-z]{3,9},?\s+\d{2,4})"
TIME_RE = r"(?P<time>\d{1,2}:\d{2}(?::\d{2})?\s?(?:AM|PM|am|pm)?)"
AMOUNT_RE = r"(?:Rs\.?|INR|₹|â‚¹)?\s?(?P<amount>[0-9,]+(?:\.\d{1,2})?)"
EVENT_RE = r"(?P<kind>paid to|sent to|sent|debited to|received from|received|credited by|payment to|collect from|transferred to)"
UPI_ID_RE = r"UPI[:\s]+(?P<upi_id>[\w.-]+@[\w]+)"

# Google Pay specific patterns
GOOGLE_PAY_RE = re.compile(
    r"(\d{1,2}(?:\s+[A-Za-z]+)?(?:\s+\d{4})?|[A-Za-z]+\s+\d{1,2}(?:\s+\d{4})?)"  # Date
    r".*?(\d{1,2}:\d{2}\s*(?:AM|PM|am|pm)?)?.*?"  # Time (optional)
    r"(sent|received|paid|debited|credited)"  # Direction
    r".*?"  # Flexible content
    r"([\d,]+(?:\.\d{2})?)\s*(?:INR|Rs\.?|₹)?",  # Amount
    re.IGNORECASE | re.DOTALL
)

DATE_FINDER = re.compile(DATE_RE, re.IGNORECASE)

# Primary pattern: date + time + event + party + amount
PATTERN_FULL = re.compile(
    rf"{DATE_RE}\s+{TIME_RE}.*?{EVENT_RE}\s+"
    rf"(?P<party>[A-Za-z0-9&@._'()\-/ ]+?)\s+{AMOUNT_RE}",
    re.IGNORECASE | re.DOTALL,
)

# Secondary pattern: date + event + party + amount (without time)
PATTERN_NO_TIME = re.compile(
    rf"{DATE_RE}\s+.*?{EVENT_RE}\s+"
    rf"(?P<party>[A-Za-z0-9&@._'()\-/ ]+?)\s+{AMOUNT_RE}",
    re.IGNORECASE | re.DOTALL,
)

# Compact pattern for dense transaction lines
PATTERN_COMPACT = re.compile(
    rf"({DATE_RE})\s+({TIME_RE})?\s+([A-Za-z0-9&@._'()\-/ ]+?)\s+({AMOUNT_RE})",
    re.IGNORECASE,
)

# VERY FLEXIBLE Google Pay pattern - catch any date + any direction + any merchant + amount
PATTERN_FLEXIBLE_GOOGLE_PAY = re.compile(
    r"(?P<date>\d{1,2}\s+[A-Za-z]+\s+\d{4}|\d{1,2}/\d{1,2}/\d{2,4})"  # Date
    r"(?:\s+(?P<time>\d{1,2}:\d{2}\s*(?:AM|PM)?))?"  # Optional time
    r".*?"  # Any content
    r"(?P<direction>sent|received|paid|debit|credit)"  # Transaction direction
    r".*?"  # More flexible content
    r"(?P<merchant>[A-Za-z0-9\s&-]+?)"  # Merchant/person
    r"\s+"  # Space
    r"(?:₹|Rs\.?|INR)?\s*"  # Optional currency
    r"(?P<amount>\d{1,3}(?:,\d{3})*(?:\.\d{2})?)",  # Amount
    re.IGNORECASE | re.DOTALL
)

PDF_PATTERNS = [PATTERN_FULL, PATTERN_NO_TIME, PATTERN_COMPACT, PATTERN_FLEXIBLE_GOOGLE_PAY]


def extract_google_pay_transactions(file_bytes: bytes, source: str) -> PdfExtractionResponse:
    """
    Extract ALL transactions from a multi-page PDF using multiple strategies.
    """
    try:
        reader = PdfReader(BytesIO(file_bytes))
    except Exception as e:
        print(f"Error reading PDF: {e}")
        return PdfExtractionResponse(source=source, transaction_count=0, transactions=[])
    
    # Extract text from all pages
    pages_text: List[str] = []
    for page_num, page in enumerate(reader.pages):
        try:
            page_text = page.extract_text() or ""
            if page_text.strip():
                pages_text.append(page_text)
        except Exception as e:
            print(f"Error extracting page {page_num}: {e}")
            continue
    
    full_text = "\n".join(pages_text)
    print(f"[PDF Extraction] Total pages: {len(reader.pages)}, Pages with text: {len(pages_text)}, Total chars: {len(full_text)}")
    
    if not full_text.strip():
        print("[PDF Extraction] WARNING: No text extracted from PDF - possibly scanned/image PDF")
        return PdfExtractionResponse(source=source, transaction_count=0, transactions=[])
    
    # Use multiple extraction strategies
    transactions: List[TransactionRecord] = []
    
    # Strategy 1: Extract by splitting on date patterns
    date_chunk_txns = _extract_by_date_chunks(full_text)
    print(f"[Strategy 1] Date chunks: {len(date_chunk_txns)} transactions")
    transactions.extend(date_chunk_txns)
    
    # Strategy 2: Extract by line-by-line parsing
    lines = [sanitize(line) for line in full_text.splitlines() if sanitize(line)]
    line_txns = []
    for line in lines:
        record = parse_line(line)
        if record and not _is_duplicate(record, transactions):
            line_txns.append(record)
            transactions.append(record)
    print(f"[Strategy 2] Line-by-line: {len(line_txns)} new transactions")
    
    # Strategy 3: Extract from consolidated text blocks
    block_txns = []
    for chunk in _extract_text_blocks(full_text):
        record = parse_line(chunk)
        if record and not _is_duplicate(record, transactions):
            block_txns.append(record)
            transactions.append(record)
    print(f"[Strategy 3] Text blocks: {len(block_txns)} new transactions")
    
    # Deduplicate and sort
    transactions = dedupe_records(transactions)
    transactions.sort(key=lambda t: (t.date, t.time or "00:00:00"))
    
    print(f"[PDF Extraction] FINAL: {len(transactions)} unique transactions extracted")
    
    return PdfExtractionResponse(
        source=source,
        transaction_count=len(transactions),
        transactions=transactions,
    )


def _extract_by_date_chunks(text: str) -> List[TransactionRecord]:
    """Extract transactions by splitting on date markers."""
    transactions: List[TransactionRecord] = []
    matches = list(DATE_FINDER.finditer(text))
    if len(matches) <= 1:
        return transactions
    
    for index, match in enumerate(matches):
        start = match.start()
        end = matches[index + 1].start() if index + 1 < len(matches) else len(text)
        chunk = sanitize(text[start:end])
        if chunk:
            record = parse_line(chunk)
            if record:
                transactions.append(record)
    
    return transactions


def _extract_text_blocks(text: str) -> List[str]:
    """Extract potential transaction blocks from text."""
    blocks: List[str] = []
    
    # Split by common block delimiters
    for delimiter in ["\n\n", "---", "___", "===", "***"]:
        if delimiter in text:
            for block in text.split(delimiter):
                if sanitize(block):
                    blocks.append(sanitize(block))
            return blocks
    
    # If no delimiters found, split by amount patterns
    amount_matches = list(re.finditer(AMOUNT_RE, text, re.IGNORECASE))
    if len(amount_matches) > 1:
        for index, match in enumerate(amount_matches):
            start = max(0, match.start() - 200)
            end = min(len(text), amount_matches[index + 1].start() if index + 1 < len(amount_matches) else len(text))
            block = text[start:end]
            if sanitize(block):
                blocks.append(sanitize(block))
    
    return blocks


def _is_duplicate(record: TransactionRecord, transactions: List[TransactionRecord]) -> bool:
    """Check if record already exists in transactions list."""
    key = (record.date.isoformat(), record.time, record.merchant_person, record.amount)
    for txn in transactions:
        txn_key = (txn.date.isoformat(), txn.time, txn.merchant_person, txn.amount)
        if key == txn_key:
            return True
    return False


def parse_line(line: str) -> Optional[TransactionRecord]:
    """Parse a single line using multiple pattern strategies."""
    if not line or len(line.strip()) < 10:
        return None
    
    # Try primary patterns first
    for pattern in [PATTERN_FULL, PATTERN_NO_TIME]:
        match = pattern.search(line)
        if match:
            record = build_record(match, line)
            if record:
                return record
    
    # Fallback to compact parsing
    record = _parse_compact_chunk(line)
    if record:
        return record
    
    # DESPERATE fallback: look for anything that could be a transaction
    # Pattern: DATE ... AMOUNT somewhere in the line
    return _desperate_parse(line)


def _desperate_parse(chunk: str) -> Optional[TransactionRecord]:
    """
    Last resort parser for detecting transactions.
    Looks for any date + any amount combination.
    """
    if not chunk or len(chunk) < 20:
        return None
    
    try:
        # Find any date
        date_matches = list(DATE_FINDER.finditer(chunk))
        if not date_matches:
            return None
        
        date_match = date_matches[0]
        parsed_date = parse_date(date_match.group("date"))
        if parsed_date is None:
            return None
        
        # Find any amount (anywhere in the chunk)
        amount_matches = list(re.finditer(AMOUNT_RE, chunk, re.IGNORECASE))
        if not amount_matches:
            return None
        
        # Try each amount (from last to first, as amounts are usually at the end)
        for amount_match in reversed(amount_matches):
            amount_str = amount_match.group("amount").replace(",", "")
            try:
                amount = float(amount_str)
            except ValueError:
                continue
            
            if amount <= 0 or amount > 10000000:  # Skip obviously wrong amounts
                continue
            
            # Try to extract merchant name
            lower_chunk = chunk.lower()
            
            # Determine type
            txn_type = "credit" if any(token in lower_chunk for token in ("received", "credited", "collect", "deposit")) else "debit"
            
            # Extract time if present
            time_matches = list(re.finditer(TIME_RE, chunk, re.IGNORECASE))
            parsed_time = normalize_time(time_matches[0].group("time")) if time_matches else None
            
            # Extract merchant name - anything between date and amount
            merchant_text = sanitize(chunk[date_match.end():amount_match.start()])
            merchant_text = re.sub(EVENT_RE, "", merchant_text, flags=re.IGNORECASE).strip(" -:/")
            
            # If merchant is still empty, try to extract from any words that aren't numbers/dates
            if not merchant_text or len(merchant_text) < 2:
                words = [w for w in merchant_text.split() if not re.match(r'^\d+$', w)]
                merchant_text = " ".join(words[:5]) if words else "Transaction"
            
            merchant = merchant_text.strip() if merchant_text and len(merchant_text) > 1 else "Transaction"
            merchant = re.sub(r'\s+', ' ', merchant).strip()
            if len(merchant) > 150:
                merchant = merchant[:150]
            
            # Success! Return the record
            return TransactionRecord(
                date=parsed_date.date(),
                time=parsed_time,
                transaction_type=txn_type,
                merchant_person=merchant,
                amount=amount,
                description=sanitize(chunk),
            )
        
        return None
    except Exception:
        return None


def build_record(match: re.Match[str], raw_line: str) -> Optional[TransactionRecord]:
    """Build a transaction record from a regex match."""
    try:
        parsed_date = parse_date(match.group("date"))
        if parsed_date is None:
            return None

        party = sanitize(match.group("party"))
        if not party or len(party) < 2:
            return None
        
        amount_str = match.group("amount").replace(",", "")
        try:
            amount = float(amount_str)
        except (ValueError, AttributeError):
            return None
        
        if amount <= 0:
            return None
        
        kind = match.group("kind").lower() if "kind" in match.groupdict() else ""
        txn_type = "credit" if any(k in kind for k in ["received", "credited", "collect"]) else "debit"
        parsed_time = normalize_time(match.groupdict().get("time"))

        return TransactionRecord(
            date=parsed_date.date(),
            time=parsed_time,
            transaction_type=txn_type,
            merchant_person=party,
            amount=amount,
            description=sanitize(raw_line),
        )
    except Exception:
        return None


def _parse_compact_chunk(chunk: str) -> Optional[TransactionRecord]:
    """Parse a transaction from a compact text block."""
    if not chunk or len(chunk) < 15:
        return None
    
    try:
        date_match = DATE_FINDER.search(chunk)
        if not date_match:
            return None

        parsed_date = parse_date(date_match.group("date"))
        if parsed_date is None:
            return None

        amount_matches = list(re.finditer(AMOUNT_RE, chunk, re.IGNORECASE))
        if not amount_matches:
            return None

        # Use the last amount found (usually the transaction amount)
        amount_match = amount_matches[-1]
        amount_str = amount_match.group("amount").replace(",", "")
        try:
            amount = float(amount_str)
        except ValueError:
            return None
        
        if amount <= 0:
            return None

        lower_chunk = chunk.lower()
        txn_type = "credit" if any(token in lower_chunk for token in ("received", "credited", "collect", "deposit", "credited by")) else "debit"
        parsed_time = normalize_time(next((match.group("time") for match in re.finditer(TIME_RE, chunk, re.IGNORECASE)), None))

        # Extract merchant name
        description = sanitize(chunk)
        merchant_text = sanitize(chunk[date_match.end():amount_match.start()])
        merchant_text = re.sub(EVENT_RE, "", merchant_text, flags=re.IGNORECASE).strip(" -:/")
        merchant = merchant_text.strip() if merchant_text.strip() else "Transaction"
        
        # Clean up merchant name
        merchant = re.sub(r'\s+', ' ', merchant).strip()
        if len(merchant) > 150:
            merchant = merchant[:150]

        return TransactionRecord(
            date=parsed_date.date(),
            time=parsed_time,
            transaction_type=txn_type,
            merchant_person=merchant,
            amount=amount,
            description=description,
        )
    except Exception as e:
        return None



def parse_date(value: str) -> Optional[datetime]:
    """Parse transaction date from various formats."""
    if not value:
        return None
    
    cleaned = value.strip().replace(",", "")
    date_formats = [
        "%d/%m/%Y", "%d/%m/%y", 
        "%d-%m-%Y", "%d-%m-%y", 
        "%d %b %Y", "%d %B %Y",
        "%d/%m/%Y", "%m/%d/%Y",
        "%d.%m.%Y", "%d-%m-%Y"
    ]
    
    for fmt in date_formats:
        try:
            return datetime.strptime(cleaned, fmt)
        except ValueError:
            continue
    return None


def normalize_time(value: Optional[str]) -> Optional[str]:
    """Normalize transaction time to HH:MM:SS format."""
    if not value:
        return None
    
    value = re.sub(r"\s+", " ", value.strip().upper())
    time_formats = ["%I:%M %p", "%I:%M:%S %p", "%H:%M", "%H:%M:%S"]
    
    for fmt in time_formats:
        try:
            return datetime.strptime(value, fmt).strftime("%H:%M:%S")
        except ValueError:
            continue
    return value


def sanitize(value: str) -> str:
    """Sanitize text by normalizing whitespace."""
    return re.sub(r"\s+", " ", (value or "").replace("₹", "INR ")).strip()



def dedupe_records(records: List[TransactionRecord]) -> List[TransactionRecord]:
    """Remove duplicate transactions while preserving order."""
    deduped: List[TransactionRecord] = []
    seen = set()
    
    for record in records:
        # Create a unique key for the transaction
        key = (
            record.date.isoformat(), 
            record.time or "", 
            record.transaction_type, 
            record.merchant_person.lower(), 
            record.amount
        )
        
        if key not in seen:
            seen.add(key)
            deduped.append(record)
    
    return deduped
